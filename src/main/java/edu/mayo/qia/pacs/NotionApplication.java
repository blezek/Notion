package edu.mayo.qia.pacs;

import com.bazaarvoice.dropwizard.assets.ConfiguredAssetsBundle;
import com.codahale.metrics.json.MetricsModule;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.spi.container.ResourceFilterFactory;
import edu.mayo.qia.pacs.components.*;
import edu.mayo.qia.pacs.db.GroupDAO;
import edu.mayo.qia.pacs.db.GroupRoleDAO;
import edu.mayo.qia.pacs.db.UserDAO;
import edu.mayo.qia.pacs.dicom.DICOMReceiver;
import edu.mayo.qia.pacs.job.AutoForwarder;
import edu.mayo.qia.pacs.managed.DBWebServer;
import edu.mayo.qia.pacs.managed.QuartzManager;
import edu.mayo.qia.pacs.rest.*;
import io.dropwizard.Application;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.hibernate.HibernateBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.shiro.web.env.IniWebEnvironment;
import org.apache.shiro.web.filter.authc.UserFilter;
import org.apache.shiro.web.filter.mgt.DefaultFilterChainManager;
import org.apache.shiro.web.filter.mgt.FilterChainManager;
import org.apache.shiro.web.filter.mgt.PathMatchingFilterChainResolver;
import org.apache.shiro.web.mgt.WebSecurityManager;
import org.apache.shiro.web.servlet.AbstractShiroFilter;
import org.eclipse.jetty.server.session.SessionHandler;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;
import org.secnod.dropwizard.shiro.ShiroConfiguration;
import org.secnod.shiro.jaxrs.ShiroExceptionMapper;
import org.secnod.shiro.jersey.ShiroResourceFilterFactory;
import org.secnod.shiro.jersey.SubjectInjectableProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

public class NotionApplication extends Application<NotionConfiguration> {
  static Logger logger = LoggerFactory.getLogger(NotionApplication.class);
  static int HashIterations = 100;

  private final HibernateBundle<NotionConfiguration> hibernate = new HibernateBundle<NotionConfiguration>(Group.class, GroupRole.class, Connector.class, Device.class, User.class, Instance.class, Item.class, MoveRequest.class, Pool.class,
      PoolContainer.class, PoolManager.class, Query.class, Result.class, Script.class, Series.class, Study.class) {
    @Override
    public DataSourceFactory getDataSourceFactory(NotionConfiguration configuration) {
      return configuration.getDataSourceFactory();
    }

    @Override
    protected void configure(org.hibernate.cfg.Configuration configuration) {
      super.configure(configuration);
      configuration.setProperty("hibernate.show_sql", "true");
      configuration.setProperty("hibernate.session.events.log", "false");
      configuration.setProperty("show_sql", "true");
    }
  };

  @Override
  public void initialize(Bootstrap<NotionConfiguration> bootstrap) {
    bootstrap.addBundle(hibernate);
    bootstrap.addBundle(new ConfiguredAssetsBundle("/public", "/", "index.html"));
  }

  @Override
  public void run(NotionConfiguration configuration, Environment environment) throws Exception {

    // Register Joda time, allows serialization/deserialization of times
    environment.getObjectMapper().registerModule(new JodaModule());
    // Metrics have their own specialized serialization
    environment.getObjectMapper().registerModule(new MetricsModule(TimeUnit.SECONDS, TimeUnit.SECONDS, true));
    environment.getObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // init Spring context
    // before we init the app context, we have to create a parent context with
    // all the config objects others rely on to get initialized
    AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
    parent.getBeanFactory().registerSingleton("executor", environment.lifecycle().executorService("Notion").build());
    parent.getBeanFactory().registerSingleton("dropwizardEnvironment", environment);
    parent.getBeanFactory().registerSingleton("sessionFactory", hibernate.getSessionFactory());
    parent.getBeanFactory().registerSingleton("configuration", configuration);
    parent.getBeanFactory().registerSingleton("objectMapper", environment.getObjectMapper());
    parent.getBeanFactory().registerSingleton("userDAO", new UserDAO(hibernate.getSessionFactory()));
    parent.getBeanFactory().registerSingleton("groupDAO", new GroupDAO(hibernate.getSessionFactory()));
    parent.getBeanFactory().registerSingleton("groupRoleDAO", new GroupRoleDAO(hibernate.getSessionFactory()));

    BasicDataSource dataSource = new BasicDataSource();
    dataSource.setUrl(configuration.getDataSourceFactory().getUrl());
    dataSource.setUsername(configuration.getDataSourceFactory().getUser());
    dataSource.setPassword(configuration.getDataSourceFactory().getPassword());
    dataSource.setDefaultAutoCommit(true);
    parent.getBeanFactory().registerSingleton("dataSource", dataSource);

    parent.refresh();
    parent.registerShutdownHook();
    parent.start();
    for (String name : parent.getBeanDefinitionNames()) {
      System.out.println("Bean: " + name);
    }

    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.setParent(parent);
    context.register(Beans.class, PoolManager.class, PoolContainer.class);
    context.scan("edu.mayo.qia.pacs.dicom");
    context.scan("edu.mayo.qia.pacs.rest");
    context.scan("edu.mayo.qia.pacs.ctp");

    context.refresh();
    context.registerShutdownHook();
    context.start();
    Notion.context = context;

    // Configure shiro with the help of org.secnod.dropwizard.shiro
    ShiroConfiguration shiroConfig = configuration.shiro;
    final IniWebEnvironment shiroEnv = new IniWebEnvironment();
    shiroEnv.setConfigLocations(shiroConfig.getIniConfigs());
    shiroEnv.init();
    shiroEnv.getWebSecurityManager();

    AbstractShiroFilter shiroFilter = new AbstractShiroFilter() {
      @Override
      public void init() throws Exception {
        WebSecurityManager securityManager = shiroEnv.getWebSecurityManager();
        setSecurityManager(securityManager);
        setFilterChainResolver(shiroEnv.getFilterChainResolver());
        if (getFilterChainResolver() == null) {
          FilterChainManager fcMan = new DefaultFilterChainManager();
          logger.debug("Filters: " + fcMan.getFilters().toString());
          Filter filter = fcMan.getFilters().get("user");
          if (filter instanceof UserFilter) {
            UserFilter uf = (UserFilter) filter;
            uf.setLoginUrl("/login.html");
          }
          fcMan.createChain("/rest/user/**", "anon");
          fcMan.createChain("/rest/pool/**", "user");
          fcMan.createChain("/rest/connector/**", "user");
          fcMan.createChain("/rest/authorization/**", "user");

          PathMatchingFilterChainResolver resolver = new PathMatchingFilterChainResolver();
          resolver.setFilterChainManager(fcMan);
          this.setFilterChainResolver(resolver);
        }
      }
    };
    ResourceConfig resourceConfig = environment.jersey().getResourceConfig();
    @SuppressWarnings("unchecked")
    List<ResourceFilterFactory> resourceFilterFactories = resourceConfig.getResourceFilterFactories();
    resourceFilterFactories.add(new ShiroResourceFilterFactory());
    environment.jersey().register(new SubjectInjectableProvider());
    environment.servlets().addFilter("ShiroFilter", shiroFilter).addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, shiroConfig.getFilterUrlPattern());
    // Register the Exception mapper to return correct responses
    environment.jersey().register(new ShiroExceptionMapper());

    if (configuration.dbWeb != null) {
      environment.lifecycle().manage(new DBWebServer(configuration.dbWeb));
    }

    environment.lifecycle().manage(context.getBean("poolManager", PoolManager.class));
    environment.lifecycle().manage(context.getBean("dicomReceiver", DICOMReceiver.class));

    environment.servlets().setSessionHandler(new SessionHandler());
    environment.jersey().setUrlPattern("/rest/*");

    // Start the global Metrics
    Metrics.register();

    // Add a component
    environment.jersey().register(context.getBean(PoolEndpoint.class));
    environment.jersey().register(context.getBean(ConnectorEndpoint.class));
    environment.jersey().register(context.getBean(UserEndpoint.class));
    environment.jersey().register(context.getBean(AuthorizationEndpoint.class));
    environment.jersey().register(context.getBean(MetricsEndpoint.class));

    QuartzManager manager = new QuartzManager(StdSchedulerFactory.getDefaultScheduler());
    environment.lifecycle().manage(manager);

    Scheduler scheduler = manager.scheduler;

    // Trigger the job to run now, and then repeat every 60 seconds
    JobDetail job = newJob(AnonymizationMapProcessor.class).withIdentity("cleanup", "alpha").build();
    Trigger trigger = newTrigger().withIdentity("trigger1", "group1").startNow().withSchedule(simpleSchedule().withIntervalInSeconds(60).repeatForever()).build();

    // Tell quartz to schedule the job using our trigger
    scheduler.scheduleJob(job, trigger);

    job = newJob(AutoForwarder.class).build();
    trigger = newTrigger().startNow().withSchedule(simpleSchedule().withIntervalInMinutes(1).repeatForever()).build();
    scheduler.scheduleJob(job, trigger);

    logger.info("\n\n=====\nStarted Notion Test:\nImageDirectory: \n" + configuration.notion.imageDirectory + "\nDBWeb:\nhttp://localhost:" + configuration.dbWeb + "\n\nDICOMPort: " + configuration.notion.dicomPort + "\n=====\n\n");

  }
}
