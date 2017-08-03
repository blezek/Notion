package edu.mayo.qia.pacs.test;

import static io.dropwizard.testing.junit.ConfigOverride.config;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.UriBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.ClientFilter;
import com.sun.jersey.core.util.MultivaluedMapImpl;

import org.apache.log4j.Logger;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.net.ConfigurationException;
import org.junit.Rule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import edu.mayo.qia.pacs.Notion;
import edu.mayo.qia.pacs.NotionApplication;
import edu.mayo.qia.pacs.NotionConfiguration;
import edu.mayo.qia.pacs.components.Connector;
import edu.mayo.qia.pacs.components.Device;
import edu.mayo.qia.pacs.components.Pool;
import edu.mayo.qia.pacs.components.Script;
import edu.mayo.qia.pacs.dicom.DcmSnd;
import edu.mayo.qia.pacs.dicom.TagLoader;

@ContextConfiguration(initializers = { PACSTest.class })
public class PACSTest implements ApplicationContextInitializer<GenericApplicationContext> {
  static Logger logger = Logger.getLogger(PACSTest.class);
  static int DICOMPort = findFreePort(30117);
  static int RESTPort = findFreePort(50118);
  static int DBPort = findFreePort(58084);
  static File tempDirectory = new File(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());
  static Client client;

  static URI baseUri = UriBuilder.fromUri("http://localhost/").port(RESTPort).path("rest").build();
  static final String JSON = MediaType.APPLICATION_JSON;

  @Autowired
  JdbcTemplate template;

  @Autowired
  ObjectMapper objectMapper;

  static {

    ClientConfig config = new DefaultClientConfig();
    client = Client.create(config);
    // add a filter to set cookies received from the server and to check if
    // login has been triggered
    // client.addFilter(new LoggingFilter());
    client.addFilter(new ClientFilter() {
      private List<Object> cookies = new ArrayList<Object>();
      private String cookie = "";

      @Override
      public ClientResponse handle(ClientRequest request) throws ClientHandlerException {
        if (cookie.length() > 0) {
          request.getHeaders().add("Cookie", cookie);
        }

        request.getHeaders().add("My-header", "foo");
        ClientResponse response = getNext().handle(request);
        // copy cookies
        for (NewCookie c : response.getCookies()) {
          if (c.getName().equals("JSESSIONID")) {
            cookie = c.getName() + "=" + c.getValue();
          }
        }
        if (response.getCookies() != null) {
          // A simple addAll just for illustration (should probably check for
          // duplicates and expired cookies)
          cookies.addAll(response.getCookies());
        }
        return response;
      }
    });
  }

  @Rule
  public TestWatcher watchman = new TestWatcher() {
    @Override
    protected void failed(Throwable e, Description description) {
      logger.error(description);
      logger.error(e.getMessage());
      logger.error("", e);
      System.out.println("\n=======================================================================");
      System.err.println(description.getMethodName() + " - TEST FAILED!");
      System.out.println(description.getMethodName() + " - TEST FAILED!");
      System.out.println("=======================================================================\n");
    }

    @Override
    protected void succeeded(Description description) {
      logger.error(description);
      System.out.println("\n=======================================================================");
      System.out.println(description.getMethodName() + " - TEST PASSED");
      System.out.println("=======================================================================\n");
    }

    @Override
    protected void starting(Description description) {
      logger.error(description);
      System.out.println("\n=======================================================================");
      System.out.println("Starting Test:  " + description.getMethodName());
      System.out.println("   Class Name:  " + description.getClassName());
      System.out.println("=======================================================================\n");
    }
  };

  protected static int findFreePort(int start) {
    boolean found = false;
    int testPort = start - 1;
    while (!found) {
      testPort = testPort + 1;
      Socket socket = null;
      try {
        socket = new Socket("localhost", testPort);
      } catch (IOException e) {
        found = true;
      } finally {
        if (socket != null) {
          try {
            socket.close();
          } catch (IOException e) {
            logger.error("Failed to find free port!", e);
          }
        }
      }
    }
    return testPort;
  }

  // @formatter:off
  public static ApplicationFixture<NotionConfiguration> NotionTestApp = new ApplicationFixture<NotionConfiguration>(NotionApplication.class,
      "notion.yml",
      config("database.url", "jdbc:derby:memory:notion;create=true"),
      config("notion.dicomPort", Integer.toString(DICOMPort)),
      config("server.connector.port", Integer.toString(RESTPort)),
      config("dbWeb", Integer.toString(DBPort)),
      config("notion.imageDirectory", tempDirectory.toString()),
      config("shiro.iniConfigs", "classpath:shiro.ini"));
  // @formatter:on

  @Override
  public synchronized void initialize(GenericApplicationContext applicationContext) {
    applicationContext.setParent(Notion.context);
    NotionTestApp.startIfRequired();

    // Create a Notion user
    URI uri = UriBuilder.fromUri(baseUri).path("user").path("register").build();
    MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
    formData.add("username", "notion");
    formData.add("password", "notion");
    formData.add("email", "empty");
    client.resource(uri).type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).post(ClientResponse.class, formData);

  }

  Pool createPool(Pool pool) {
    ClientResponse response = null;
    URI uri = UriBuilder.fromUri(baseUri).path("/pool").build();
    response = client.resource(uri).type(JSON).accept(JSON).post(ClientResponse.class, pool);
    assertEquals("Got result", 200, response.getStatus());
    pool = response.getEntity(Pool.class);
    assertTrue("Assigned an id", pool.poolKey != 0);
    return pool;

  }

  Device createDevice(Device device) {
    // Create a device
    URI uri = UriBuilder.fromUri(baseUri).path("/pool/" + device.getPool().poolKey + "/device").build();
    ClientResponse response = client.resource(uri).type(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, device);
    assertEquals("Got result", 200, response.getStatus());
    device = response.getEntity(Device.class);
    logger.info("Entity back: " + device);
    assertTrue("Assigned a deviceKey", device.deviceKey != 0);
    return device;

  }

  Connector createConnector(Connector connector) {
    // Create a device
    URI uri = UriBuilder.fromUri(baseUri).path("/connector").build();
    ClientResponse response = client.resource(uri).type(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, connector);
    assertEquals("Got result", 200, response.getStatus());
    connector = response.getEntity(Connector.class);
    logger.info("Entity back: " + connector);
    assertTrue("Assigned a key", connector.connectorKey != 0);
    return connector;

  }

  Script createScript(Script script) {
    // Create a device
    URI uri = UriBuilder.fromUri(baseUri).path("/pool/" + script.getPool().poolKey + "/script").build();
    ClientResponse response = client.resource(uri).type(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).put(ClientResponse.class, script);
    assertEquals("Got result", 200, response.getStatus());
    script = response.getEntity(Script.class);
    logger.info("Entity back: " + script);
    assertTrue("Assigned a scriptKey", script.scriptKey != 0);
    return script;
  }

  protected List<File> sendDICOM(String called, String calling, String series) throws IOException, ConfigurationException, InterruptedException {
    // Send the files
    DcmSnd sender = new DcmSnd("test");
    // Startup the sender
    sender.setRemoteHost("localhost");
    sender.setRemotePort(DICOMPort);
    sender.setCalledAET(called);
    sender.setCalling(calling);
    List<File> testSeries = getTestSeries(series);
    for (File f : testSeries) {
      if (f.isFile()) {
        sender.addFile(f);
      }
    }
    sender.configureTransferCapability();
    sender.open();
    sender.send(null);
    sender.close();
    return testSeries;
  }

  List<DicomObject> getTags(String pattern) throws Exception {
    return getTags(getTestSeries(pattern));
  }

  protected List<DicomObject> getTags(List<File> files) throws Exception {
    List<DicomObject> list = new ArrayList<DicomObject>();
    for (File file : files) {
      list.add(TagLoader.loadTags(file));
    }
    return list;
  }

  protected List<File> getTestSeries(String resource_pattern) throws IOException {
    // Load some files
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    Resource[] resources = resolver.getResources("classpath:" + resource_pattern);
    List<File> files = new ArrayList<File>();
    for (Resource resource : resources) {
      files.add(resource.getFile());
    }
    return files;
  }

}
