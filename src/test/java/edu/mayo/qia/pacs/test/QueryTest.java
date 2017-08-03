package edu.mayo.qia.pacs.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.sql.DataSource;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.multipart.FormDataMultiPart;
import com.sun.jersey.multipart.file.FileDataBodyPart;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

import edu.mayo.qia.pacs.Notion;
import edu.mayo.qia.pacs.components.Connector;
import edu.mayo.qia.pacs.components.Device;
import edu.mayo.qia.pacs.components.Item;
import edu.mayo.qia.pacs.components.Pool;
import edu.mayo.qia.pacs.components.Query;
import edu.mayo.qia.pacs.components.Result;

@RunWith(SpringJUnit4ClassRunner.class)
public class QueryTest extends PACSTest {

  @Autowired
  SessionFactory sessionFactory;

  @Autowired
  JdbcTemplate template;

  @Autowired
  DataSource dataSource;

  InputStream getResource(String fileName) throws Exception {
    Resource resource = Notion.context.getResource("classpath:" + fileName);
    return resource.getInputStream();
  }

  @Test
  public void xls() throws Exception {

    InputStream is = getResource("Query/QueryTemplate.xls");

    // Get the workbook instance for XLS file
    Workbook workbook = new HSSFWorkbook(is);

    assertTrue("Expected more than 0 worksheets", workbook.getNumberOfSheets() > 0);

    // Get first sheet from the workbook
    Sheet sheet = workbook.getSheetAt(0);

    assertEquals(3, sheet.getPhysicalNumberOfRows());
    assertEquals(9, sheet.getRow(0).getPhysicalNumberOfCells());

    assertEquals("PatientName", sheet.getRow(0).getCell(0).getStringCellValue());
    assertEquals("Hurt John", sheet.getRow(1).getCell(0).getStringCellValue());
    is.close();
  }

  @Test
  public void autoCommit() throws Exception {
    assertTrue("Autocommit on dataSource", dataSource.getConnection().getAutoCommit());
    assertTrue("Autocommit on template", template.getDataSource().getConnection().getAutoCommit());
  }

  @Test
  public void xlsx() throws Exception {

    InputStream is = getResource("Query/QueryTemplate.xlsx");

    // Get the workbook instance for XLS file
    Workbook workbook = new XSSFWorkbook(is);

    // Get first sheet from the workbook
    Sheet sheet = workbook.getSheetAt(0);

    assertEquals(4, sheet.getPhysicalNumberOfRows());
    assertEquals(9, sheet.getRow(0).getPhysicalNumberOfCells());

    assertEquals("PatientName", sheet.getRow(0).getCell(0).getStringCellValue());
    assertEquals("Hurt John", sheet.getRow(1).getCell(0).getStringCellValue());
    is.close();
  }

  @Test
  public void constructFromXLSX() throws Exception {
    Query query;
    UUID uid = UUID.randomUUID();
    String aet = uid.toString().substring(0, 10);
    Pool pool = new Pool(aet, aet, aet, true);
    pool = createPool(pool);
    Device device = new Device(".*", ".*", 1234, pool);
    device = createDevice(device);

    Session session = sessionFactory.openSession();
    InputStream is = getResource("Query/QueryTemplate.xlsx");
    try {
      session.beginTransaction();
      query = Query.constructQuery("QueryTemplate.xlsx", is);
      query.pool = pool;
      query.destinationPool = pool;
      query.queryPool = pool;
      query.device = device;
      session.save(query);
      assertEquals(3, query.items.size());
      session.getTransaction().commit();
    } finally {
      is.close();
      session.close();
    }
    assertEquals(3, (int) template.queryForObject("select count(*) from QUERYITEM where QueryKey = ?", Integer.class, query.queryKey));
    assertEquals(1, (int) template.queryForObject("select count(*) from QUERYITEM where QueryKey = ? and AnonymizedName is null and AnonymizedID is null", Integer.class, query.queryKey));

  }

  @Test
  public void executeQuery() throws Exception {
    // My pool
    UUID uid = UUID.randomUUID();
    String aet = uid.toString().substring(0, 10);
    aet = "myPool";
    Pool pool = new Pool(aet, aet, aet, true);
    pool = createPool(pool);

    // "PACS" pool
    uid = UUID.randomUUID();
    aet = uid.toString().substring(0, 10);
    aet = "PACS";
    Pool pacsPool = createPool(new Pool(aet, aet, aet, false));

    // "destination" pool
    uid = UUID.randomUUID();
    aet = uid.toString().substring(0, 10);
    aet = "destination";
    Pool destinationPool = createPool(new Pool(aet, aet, aet, false));

    // Flow of images is:
    // PACS -> destinationPool -> pool

    // Let the PACS pool know about the destination pool, and vice versa
    createDevice(new Device(destinationPool.applicationEntityTitle, "localhost", DICOMPort, pacsPool));
    createDevice(new Device(pacsPool.applicationEntityTitle, "localhost", DICOMPort, destinationPool));
    // The device we will use to query the "PACS" pool
    Device queryDevice = createDevice(new Device(pacsPool.applicationEntityTitle, "localhost", DICOMPort, destinationPool.applicationEntityTitle, destinationPool));

    // create the connector
    Connector connector = new Connector();
    connector.name = "Connection to PACS";
    connector.destinationPoolKey = destinationPool.poolKey;
    connector.queryPoolKey = destinationPool.poolKey;
    connector.queryDeviceKey = queryDevice.deviceKey;
    connector = createConnector(connector);

    // Send some test data to the PACS pool
    sendDICOM(pacsPool.applicationEntityTitle, destinationPool.applicationEntityTitle, "TOF/IMAGE001.dcm");

    // Upload our query
    URI uri = UriBuilder.fromUri(baseUri).path("/pool").path(Integer.toString(pool.poolKey)).path("query").build();
    WebResource fileResource = client.resource(uri);
    final FormDataMultiPart multiPart = new FormDataMultiPart();
    multiPart.bodyPart(new FileDataBodyPart("file", Notion.context.getResource("classpath:Query/QueryTemplate.xlsx").getFile(), MediaType.APPLICATION_OCTET_STREAM_TYPE));
    multiPart.field("connectorKey", Integer.toString(connector.connectorKey));

    ClientResponse response = fileResource.type(MediaType.MULTIPART_FORM_DATA_TYPE).post(ClientResponse.class, multiPart);

    Query query = response.getEntity(Query.class);

    uri = UriBuilder.fromUri(baseUri).path("/pool").path(Integer.toString(pool.poolKey)).path("query").path(Integer.toString(query.queryKey)).build();
    logger.debug("Loading: " + uri);
    response = client.resource(uri).accept(JSON).get(ClientResponse.class);
    assertEquals("Got result", 200, response.getStatus());

    // Grab the data until the query finishes, then fetch and verify
    uri = UriBuilder.fromUri(baseUri).path("/pool").path(Integer.toString(pool.poolKey)).path("query").path(Integer.toString(query.queryKey)).build();
    for (int i = 0; i < 5; i++) {
      logger.debug("Loading: " + uri);
      response = client.resource(uri).accept(JSON).get(ClientResponse.class);
      assertEquals("Got result", 200, response.getStatus());
      query = response.getEntity(Query.class);
      if (query.status.startsWith("Query Completed")) {
        break;
      } else {
        Thread.sleep(400);
      }
    }
    assertTrue(query.status.startsWith("query completed"));

    assertEquals(3, (int) template.queryForObject("select count(*) from QUERYITEM where QueryKey = ?", new Object[] { query.queryKey }, Integer.class));

    // Update the query to fetch any results
    int sum = 0;
    for (Item item : query.items) {
      for (Result result : item.items) {
        result.doFetch = true;
        sum++;
      }
    }
    // Should have 1 study to fetch
    assertEquals(1, sum);

    response = client.resource(uri).type(JSON).accept(JSON).put(ClientResponse.class, query);
    assertEquals(200, response.getStatus());

    // Trigger the fetch
    uri = UriBuilder.fromUri(baseUri).path("/pool").path(Integer.toString(pool.poolKey)).path("query").path(Integer.toString(query.queryKey)).path("fetch").build();
    response = client.resource(uri).accept(JSON).put(ClientResponse.class);
    assertEquals(200, response.getStatus());

    // Wait for things to finish
    uri = UriBuilder.fromUri(baseUri).path("/pool").path(Integer.toString(pool.poolKey)).path("query").path(Integer.toString(query.queryKey)).build();
    for (int i = 0; i < 15; i++) {
      logger.debug("Loading: " + uri);
      response = client.resource(uri).accept(JSON).get(ClientResponse.class);
      assertEquals("Got result", 200, response.getStatus());
      query = response.getEntity(Query.class);
      logger.info("Got response: " + query.status);
      if (query.status.startsWith("Fetch Completed")) {
        break;
      } else {
        // Ugly, but it works
        Thread.sleep(1000);
      }
    }
    assertTrue("Waiting for fetch to be completed -- " + query.status, query.status.startsWith("fetch completed"));

    // The pool should have 1
    assertEquals(1, (int) template.queryForObject("select count(*) from STUDY where PoolKey = ?", new Object[] { pool.poolKey }, Integer.class));
    // The destination pool should not have any studies
    assertEquals("is study deleted from the destination pool?", 0, (int) template.queryForObject("select count(*) from STUDY where PoolKey = ?", new Object[] { destinationPool.poolKey }, Integer.class));
    // The PACS pool should have 1
    assertEquals(1, (int) template.queryForObject("select count(*) from STUDY where PoolKey = ?", new Object[] { pacsPool.poolKey }, Integer.class));

  }

  @Test
  public void executeSimpleQuery() throws Exception {
    // My pool
    String aet;
    aet = "myPoolSimple";
    Pool pool = new Pool(aet, aet, aet, true);
    pool = createPool(pool);

    // "PACS" pool
    aet = "PACSSimple";
    Pool pacsPool = createPool(new Pool(aet, aet, aet, false));

    // "destination" pool
    aet = "destinationS";
    Pool destinationPool = createPool(new Pool(aet, aet, aet, false));

    // Flow of images is:
    // PACS -> destinationPool -> pool

    // Let the PACS pool know about the destination pool, and vice versa
    createDevice(new Device(destinationPool.applicationEntityTitle, "localhost", DICOMPort, pacsPool));
    createDevice(new Device(pacsPool.applicationEntityTitle, "localhost", DICOMPort, destinationPool));
    // The device we will use to query the "PACS" pool
    Device queryDevice = createDevice(new Device(pacsPool.applicationEntityTitle, "localhost", DICOMPort, destinationPool.applicationEntityTitle, destinationPool));

    // create the connector
    Connector connector = new Connector();
    connector.name = "Connection to PACS For Simple Queries";
    connector.destinationPoolKey = destinationPool.poolKey;
    connector.queryPoolKey = destinationPool.poolKey;
    connector.queryDeviceKey = queryDevice.deviceKey;
    connector = createConnector(connector);

    // Send some test data to the PACS pool
    sendDICOM(pacsPool.applicationEntityTitle, destinationPool.applicationEntityTitle, "TOF/IMAGE001.dcm");

    // Upload our query
    URI uri = UriBuilder.fromUri(baseUri).path("/pool").path(Integer.toString(pool.poolKey)).path("query").path("simple").build();

    ObjectNode node = new ObjectMapper().createObjectNode();
    node.put("connectorKey", connector.connectorKey);
    ObjectNode it = node.putArray("items").addObject();
    it.put("PatientID", "MRA-0068");

    ClientResponse response = client.resource(uri).type(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).put(ClientResponse.class, node);

    Query query = response.getEntity(Query.class);

    uri = UriBuilder.fromUri(baseUri).path("/pool").path(Integer.toString(pool.poolKey)).path("query").path(Integer.toString(query.queryKey)).build();
    logger.debug("Loading: " + uri);
    response = client.resource(uri).accept(JSON).get(ClientResponse.class);
    assertEquals("Got result", 200, response.getStatus());

    // Grab the data until the query finishes, then fetch and verify
    uri = UriBuilder.fromUri(baseUri).path("/pool").path(Integer.toString(pool.poolKey)).path("query").path(Integer.toString(query.queryKey)).build();
    for (int i = 0; i < 5; i++) {
      logger.debug("Loading: " + uri);
      response = client.resource(uri).accept(JSON).get(ClientResponse.class);
      assertEquals("Got result", 200, response.getStatus());
      query = response.getEntity(Query.class);
      if (query.status.startsWith("Query Completed")) {
        break;
      } else {
        Thread.sleep(400);
      }
    }
    assertTrue(query.status.startsWith("query completed"));

    assertEquals(1, (int) template.queryForObject("select count(*) from QUERYITEM where QueryKey = ?", new Object[] { query.queryKey }, Integer.class));

    // Update the query to fetch any results
    int sum = 0;
    for (Item item : query.items) {
      for (Result result : item.items) {
        result.doFetch = true;
        sum++;
      }
    }
    // Should have 1 study to fetch
    assertEquals(1, sum);

    response = client.resource(uri).type(JSON).accept(JSON).put(ClientResponse.class, query);
    assertEquals(200, response.getStatus());

    // Trigger the fetch
    uri = UriBuilder.fromUri(baseUri).path("/pool").path(Integer.toString(pool.poolKey)).path("query").path(Integer.toString(query.queryKey)).path("fetch").build();
    response = client.resource(uri).accept(JSON).put(ClientResponse.class);
    assertEquals(200, response.getStatus());

    // Wait for things to finish
    uri = UriBuilder.fromUri(baseUri).path("/pool").path(Integer.toString(pool.poolKey)).path("query").path(Integer.toString(query.queryKey)).build();
    for (int i = 0; i < 15; i++) {
      logger.debug("Loading: " + uri);
      response = client.resource(uri).accept(JSON).get(ClientResponse.class);
      assertEquals("Got result", 200, response.getStatus());
      query = response.getEntity(Query.class);
      logger.info("Got response: " + query.status);
      if (query.status.startsWith("fetch completed")) {
        break;
      } else {
        // Ugly, but it works
        Thread.sleep(1000);
      }
    }
    assertTrue("Waiting for fetch to be completed -- " + query.status, query.status.startsWith("fetch completed"));

    // The pool should have 1
    assertEquals(1, (int) template.queryForObject("select count(*) from STUDY where PoolKey = ?", new Object[] { pool.poolKey }, Integer.class));
    // The destination pool should not have any studies
    assertEquals("is study deleted from the destination pool?", 0, (int) template.queryForObject("select count(*) from STUDY where PoolKey = ?", new Object[] { destinationPool.poolKey }, Integer.class));
    // The PACS pool should have 1
    assertEquals(1, (int) template.queryForObject("select count(*) from STUDY where PoolKey = ?", new Object[] { pacsPool.poolKey }, Integer.class));

  }

}
