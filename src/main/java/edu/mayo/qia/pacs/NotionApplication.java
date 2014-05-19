package edu.mayo.qia.pacs;

import io.dropwizard.Application;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.hibernate.HibernateBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

import org.apache.commons.dbcp.BasicDataSource;
import org.eclipse.jetty.server.session.SessionHandler;
import org.secnod.dropwizard.shiro.ShiroBundle;
import org.secnod.dropwizard.shiro.ShiroConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.bazaarvoice.dropwizard.assets.ConfiguredAssetsBundle;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;

import edu.mayo.qia.pacs.components.Connector;
import edu.mayo.qia.pacs.components.Device;
import edu.mayo.qia.pacs.components.Instance;
import edu.mayo.qia.pacs.components.Item;
import edu.mayo.qia.pacs.components.MoveRequest;
import edu.mayo.qia.pacs.components.Pool;
import edu.mayo.qia.pacs.components.PoolContainer;
import edu.mayo.qia.pacs.components.PoolManager;
import edu.mayo.qia.pacs.components.Query;
import edu.mayo.qia.pacs.components.Result;
import edu.mayo.qia.pacs.components.Script;
import edu.mayo.qia.pacs.components.Series;
import edu.mayo.qia.pacs.components.Study;
import edu.mayo.qia.pacs.components.User;
import edu.mayo.qia.pacs.db.UserDAO;
import edu.mayo.qia.pacs.dicom.DICOMReceiver;
import edu.mayo.qia.pacs.managed.DBWebServer;
import edu.mayo.qia.pacs.rest.ConnectorEndpoint;
import edu.mayo.qia.pacs.rest.PoolEndpoint;
import edu.mayo.qia.pacs.rest.UserEndpoint;

public class NotionApplication extends Application<NotionConfiguration> {
  static Logger logger = LoggerFactory.getLogger(NotionApplication.class);
  static int HashIterations = 100;

  private final HibernateBundle<NotionConfiguration> hibernate = new HibernateBundle<NotionConfiguration>(Connector.class, Device.class, User.class, Instance.class, Item.class, MoveRequest.class, Pool.class, PoolContainer.class, PoolManager.class,
      Query.class, Result.class, Script.class, Series.class, Study.class, Connector.class) {
    @Override
    public DataSourceFactory getDataSourceFactory(NotionConfiguration configuration) {
      return configuration.getDataSourceFactory();
    }

    @Override
    protected void configure(org.hibernate.cfg.Configuration configuration) {
      super.configure(configuration);
      configuration.setProperty("hibernate.show_sql", "true");
      configuration.setProperty("show_sql", "true");
    }
  };

  private final ShiroBundle<NotionConfiguration> shiro = new ShiroBundle<NotionConfiguration>() {

    @Override
    protected ShiroConfiguration narrow(NotionConfiguration configuration) {
      return configuration.shiro;
    }
  };

  @Override
  public void initialize(Bootstrap<NotionConfiguration> bootstrap) {
    bootstrap.addBundle(hibernate);
    bootstrap.addBundle(shiro);
    bootstrap.addBundle(new ConfiguredAssetsBundle("/public", "/", "index.html"));
  }

  @Override
  public void run(NotionConfiguration configuration, Environment environment) throws Exception {

    // Register Joda time
    environment.getObjectMapper().registerModule(new JodaModule());
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

    if (configuration.dbWeb != null) {
      environment.lifecycle().manage(new DBWebServer(configuration.dbWeb));
    }

    environment.lifecycle().manage(context.getBean("poolManager", PoolManager.class));
    environment.lifecycle().manage(context.getBean(DICOMReceiver.class));

    environment.servlets().setSessionHandler(new SessionHandler());
    environment.jersey().setUrlPattern("/rest/*");

    // Add a component
    environment.jersey().register(context.getBean(PoolEndpoint.class));
    environment.jersey().register(context.getBean(ConnectorEndpoint.class));
    environment.jersey().register(context.getBean(UserEndpoint.class));

    logger.info("\n\n=====\nStarted Notion Test:\nImageDirectory: \n" + configuration.notion.imageDirectory + "\nDBWeb:\nhttp://localhost:" + configuration.dbWeb + "\n\nDICOMPort: " + configuration.notion.dicomPort + "\n=====\n\n");

  }
}