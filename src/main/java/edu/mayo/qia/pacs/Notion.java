package edu.mayo.qia.pacs;

import com.codahale.metrics.MetricRegistry;

import org.slf4j.Logger;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Notion {

  static {
    System.setProperty("java.awt.headless", "true");
  }

  public static void main(String[] args) throws Exception {
    System.setProperty("java.awt.headless", "true");
    new NotionApplication().run(args);
  }

  public static void checkAssertion(boolean v, String message) throws Exception {
    if (!v) {
      throw new Exception(message);
    }
  }

  public static AnnotationConfigApplicationContext context;
  public static ExecutorService executor = Executors.newCachedThreadPool();
  public static String version = "3.1.0";
  public static final MetricRegistry metrics = new MetricRegistry();
  public static Logger audit = Audit.logger;

}
