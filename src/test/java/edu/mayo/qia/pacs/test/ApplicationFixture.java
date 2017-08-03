package edu.mayo.qia.pacs.test;

import com.google.common.collect.ImmutableMap;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.cli.ServerCommand;
import io.dropwizard.lifecycle.ServerLifecycleListener;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.ConfigOverride;
import net.sourceforge.argparse4j.inf.Namespace;

/**
 * A JUnit rule for starting and stopping your application at the start and end
 * of a test class.
 * <p/>
 * By default, the {@link Application} will be constructed using reflection to
 * invoke the nullary constructor. If your application does not provide a public
 * nullary constructor, you will need to override the {@link #newApplication()}
 * method to provide your application instance(s).
 * 
 * @param <C>
 *        the configuration type
 */
public class ApplicationFixture<C extends Configuration> {

  private final Class<? extends Application<C>> applicationClass;
  private final String configPath;

  private C configuration;
  private Application<C> application;
  private Environment environment;
  private Server jettyServer;

  public ApplicationFixture(Class<? extends Application<C>> applicationClass, String configPath, ConfigOverride... configOverrides) {
    this.applicationClass = applicationClass;
    this.configPath = configPath;
    for (ConfigOverride configOverride : configOverrides) {
      configOverride.addToSystemProperties();
    }
    startIfRequired();
  }

  public void startIfRequired() {
    if (jettyServer != null) {
      return;
    }

    try {
      application = newApplication();

      final Bootstrap<C> bootstrap = new Bootstrap<C>(application) {
        @Override
        public void run(C configuration, Environment environment) throws Exception {
          environment.lifecycle().addServerLifecycleListener(new ServerLifecycleListener() {
            @Override
            public void serverStarted(Server server) {
              jettyServer = server;
            }
          });
          ApplicationFixture.this.configuration = configuration;
          ApplicationFixture.this.environment = environment;
          super.run(configuration, environment);
        }
      };

      application.initialize(bootstrap);
      final ServerCommand<C> command = new ServerCommand<C>(application);
      final Namespace namespace = new Namespace(ImmutableMap.<String, Object> of("file", configPath));
      command.run(bootstrap, namespace);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public C getConfiguration() {
    return configuration;
  }

  public int getLocalPort() {
    return ((ServerConnector) jettyServer.getConnectors()[0]).getLocalPort();
  }

  public Application<C> newApplication() {
    try {
      return applicationClass.newInstance();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("unchecked")
  public <A extends Application<C>> A getApplication() {
    return (A) application;
  }

  public Environment getEnvironment() {
    return environment;
  }
}
