package edu.mayo.qia.pacs.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.ws.rs.core.UriBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.jersey.api.client.ClientResponse;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.net.URI;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import edu.mayo.qia.pacs.components.Device;
import edu.mayo.qia.pacs.components.Pool;

@RunWith(SpringJUnit4ClassRunner.class)
public class StudiesTest extends PACSTest {

  @Test
  public void fetchStudiesList() throws Exception {
    UUID uid = UUID.randomUUID();
    String aet = uid.toString().substring(0, 10);
    Pool pool = new Pool(aet, aet, aet, false);
    pool = createPool(pool);
    Device device = new Device(".*", ".*", 1234, pool);
    device = createDevice(device);

    sendDICOM(aet, aet, "TOF/*001.dcm");
    assertEquals("DB", new Integer(1), template.queryForObject("select count(*) from STUDY where PoolKey = " + pool.poolKey, Integer.class));

    ClientResponse response = null;
    URI uri = UriBuilder.fromUri(baseUri).path("/pool/" + pool.poolKey + "/studies").build();
    ObjectNode json = new ObjectMapper().createObjectNode();
    response = client.resource(uri).type(JSON).accept(JSON).post(ClientResponse.class, json);
    assertEquals("Got result", 200, response.getStatus());
    json = response.getEntity(ObjectNode.class);
    assertTrue("Result", json.has("Result"));
    assertTrue("Records", json.has("Records"));
    assertEquals("Count", 1, json.withArray("Records").size());
    assertEquals("Entry", "MRA-0068", json.withArray("Records").get(0).get("PatientID").textValue());

    // Delete, need to use the Form object
    String studyKey = Integer.toString(json.withArray("Records").get(0).get("StudyKey").intValue());
    uri = UriBuilder.fromUri(baseUri).path("/pool/" + pool.poolKey + "/studies/").path(studyKey).build();
    response = client.resource(uri).delete(ClientResponse.class);
    assertEquals("Got result", 200, response.getStatus());
    // Query the DB
    assertEquals("DB", new Integer(0), template.queryForObject("select count(*) from STUDY where PoolKey = " + pool.poolKey, Integer.class));

  }

  @Test
  public void zip() throws Exception {
    UUID uid = UUID.randomUUID();
    String aet = uid.toString().substring(0, 10);
    Pool pool = new Pool(aet, aet, aet, false);
    pool = createPool(pool);
    Device device = new Device(".*", ".*", 1234, pool);
    device = createDevice(device);

    sendDICOM(aet, aet, "TOF/*001.dcm");

    ClientResponse response = null;
    URI uri = UriBuilder.fromUri(baseUri).path("/pool/" + pool.poolKey + "/studies").build();
    ObjectNode json = new ObjectMapper().createObjectNode();
    response = client.resource(uri).type(JSON).accept(JSON).post(ClientResponse.class, json);
    assertEquals("Got result", 200, response.getStatus());
    json = response.getEntity(ObjectNode.class);

    // Delete, need to use the Form object
    String studyKey = Integer.toString(json.withArray("Records").get(0).get("StudyKey").intValue());
    uri = UriBuilder.fromUri(baseUri).path("/pool/" + pool.poolKey + "/studies/").path(studyKey).path("zip").build();
    response = client.resource(uri).get(ClientResponse.class);
    assertEquals("Got result", 200, response.getStatus());
    ZipInputStream unzip = new ZipInputStream(response.getEntityInputStream());
    ZipEntry dir = unzip.getNextEntry();
    assertTrue(dir != null);
    assertTrue(dir.getName().startsWith("MRA-0068/MRA_v_Hd_wo-2008-06-18/"));
    unzip.close();
  }

  @Test
  public void zipStudies() throws Exception {
    UUID uid = UUID.randomUUID();
    String aet = uid.toString().substring(0, 10);
    Pool pool = new Pool(aet, aet, aet, false);
    pool = createPool(pool);
    Device device = new Device(".*", ".*", 1234, pool);
    device = createDevice(device);

    sendDICOM(aet, aet, "TOF/*001.dcm");

    // Delete, need to use the Form object
    URI uri = UriBuilder.fromUri(baseUri).path("/pool/" + pool.poolKey + "/studies/zip").build();
    ClientResponse response = client.resource(uri).get(ClientResponse.class);
    assertEquals("Got result", 200, response.getStatus());
    ZipInputStream unzip = new ZipInputStream(response.getEntityInputStream());
    ZipEntry dir = unzip.getNextEntry();
    assertTrue(dir != null);
    assertTrue(dir.getName().startsWith(pool.name + "-StudyDownload"));
    unzip.close();
  }

}
