package edu.mayo.qia.pacs;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecode.flyway.core.Flyway;

import org.apache.log4j.Logger;
import org.apache.shiro.authz.AuthorizationInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.ext.beans.BeansWrapper;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.Version;

@Configuration
@EnableScheduling
@EnableAsync
public class Beans {
  static Logger logger = Logger.getLogger(Beans.class);

  @Autowired
  NotionConfiguration configuration;

  @Autowired
  DataSource dataSource;

  @Bean
  freemarker.template.Configuration freemarker() throws IOException {
    // Freemarker
    BeansWrapper.getDefaultInstance().setExposeFields(true);
    freemarker.template.Configuration cfg = new freemarker.template.Configuration();
    // Where do we load the templates from:

    ArrayList<TemplateLoader> loaders = new ArrayList<TemplateLoader>();
    if (configuration.notion.templatePath != null) {
      loaders.add(new FileTemplateLoader(new File(configuration.notion.templatePath)));
    }
    loaders.add(new ClassTemplateLoader(this.getClass(), "/templates"));
    cfg.setTemplateLoader(new MultiTemplateLoader(loaders.toArray(new TemplateLoader[] {})));

    // Some other recommended settings:
    cfg.setIncompatibleImprovements(new Version(2, 3, 20));
    cfg.setDefaultEncoding("UTF-8");
    cfg.setLocale(Locale.US);
    DefaultObjectWrapper ow = new DefaultObjectWrapper();
    ow.setExposeFields(true);
    ow.setExposureLevel(BeansWrapper.EXPOSE_SAFE);
    cfg.setObjectWrapper(ow);
    cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    return cfg;
  }

  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  @Bean
  @DependsOn("flyway")
  public JdbcTemplate template() throws SQLException {
    JdbcTemplate template = new JdbcTemplate();
    template.setDataSource(dataSource);
    return template;
  }

  @Bean
  @DependsOn("dataSource")
  public Flyway flyway() throws Exception {
    Flyway flyway = new Flyway();
    flyway.setDataSource(dataSource);
    flyway.migrate();
    return flyway;
  }

  @Bean
  public TaskScheduler taskScheduler() {
    ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
    s.setPoolSize(10);
    return s;
  }

  @Bean
  public ExecutorService executor() {
    return Notion.executor;
  }

  @Bean
  public Map<String, AuthorizationInfo> authorizationCache() {
    return new ConcurrentHashMap<String, AuthorizationInfo>();
  }

}
