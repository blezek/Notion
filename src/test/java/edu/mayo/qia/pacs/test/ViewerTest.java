package edu.mayo.qia.pacs.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.jersey.api.client.ClientResponse;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.net.URI;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.UUID;

import edu.mayo.qia.pacs.components.Device;
import edu.mayo.qia.pacs.components.Pool;

@RunWith(SpringJUnit4ClassRunner.class)
public class ViewerTest extends PACSTest {
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
    URI uri = UriBuilder.fromUri(baseUri).path("/pool/" + pool.poolKey + "/viewer/studies").build();
    ObjectNode json = new ObjectMapper().createObjectNode();
    response = client.resource(uri).type(MediaType.APPLICATION_JSON).accept(JSON).get(ClientResponse.class);
    assertEquals("Got result", 200, response.getStatus());
    json = response.getEntity(ObjectNode.class);

    assertTrue("has studyList array", json.has("studyList") && json.get("studyList").isArray());
    ArrayNode studyList = json.withArray("studyList");
    assertTrue("length studyList > 0", studyList.size() > 0);
    JsonNode study = studyList.get(0);

    HashMap<String, String> lookup = new HashMap<String, String>();
    lookup.put("patientName", "MRA-0068");
    lookup.put("patientId", "MRA-0068");
    lookup.put("studyDate", "2008-06-18 00:00:00.0");
    lookup.put("modality", "unknown");
    lookup.put("studyDescription", "MRA/v Hd wo");

    for (Entry<String, String> entry : lookup.entrySet()) {
      assertEquals("looking for " + entry.getKey() + " = " + entry.getValue(), entry.getValue(), study.get(entry.getKey()).textValue());
    }

    // Get the series list
    String studyId = study.get("studyId").textValue();
    uri = UriBuilder.fromUri(baseUri).path("/pool/" + pool.poolKey + "/viewer/study/" + studyId + "/series").build();
    json = new ObjectMapper().createObjectNode();
    response = client.resource(uri).type(MediaType.APPLICATION_JSON).accept(JSON).get(ClientResponse.class);
    assertEquals("Got result", 200, response.getStatus());
    json = response.getEntity(ObjectNode.class);

    for (String key : lookup.keySet()) {
      assertEquals("comparing " + key, json.get(key).textValue(), study.get(key).textValue());
    }

    // Check length of series list
    ArrayNode seriesList = json.withArray("seriesList");
    assertEquals("have series", 2, seriesList.size());

  }

  @Test
  public void anonymous() throws Exception {
    UUID uid = UUID.randomUUID();
    String aet = uid.toString().substring(0, 10);
    Pool pool = new Pool(aet, aet, aet, false);
    pool = createPool(pool);
    Device device = new Device(".*", ".*", 1234, pool);
    device = createDevice(device);

    sendDICOM(aet, aet, "TOF/*001.dcm");
    assertEquals("DB", new Integer(1), template.queryForObject("select count(*) from STUDY where PoolKey = " + pool.poolKey, Integer.class));
    Integer studyKey = template.queryForObject("select StudyKey from STUDY where PoolKey = " + pool.poolKey, Integer.class);

    ClientResponse response = null;
    URI uri = UriBuilder.fromUri(baseUri).path("/pool/" + pool.poolKey + "/studies/" + studyKey + "/hash").build();
    ObjectNode json = new ObjectMapper().createObjectNode();
    response = client.resource(uri).type(MediaType.APPLICATION_JSON).accept(JSON).get(ClientResponse.class);
    assertEquals("Got result", 200, response.getStatus());
    json = response.getEntity(ObjectNode.class);

    // did we create a hash?
    assertTrue("created hash", json.has("Hash"));
    String hash = json.get("Hash").asText();

    // Get the series
    uri = UriBuilder.fromUri(baseUri).path("/viewer/" + hash + "/series/").build();
    json = new ObjectMapper().createObjectNode();
    response = client.resource(uri).type(MediaType.APPLICATION_JSON).accept(JSON).get(ClientResponse.class);
    assertEquals("Got result", 200, response.getStatus());
    json = response.getEntity(ObjectNode.class);

    HashMap<String, String> lookup = new HashMap<String, String>();
    lookup.put("patientName", "MRA-0068");
    lookup.put("patientId", "MRA-0068");
    lookup.put("studyDate", "2008-06-18 00:00:00.0");
    lookup.put("modality", "unknown");
    lookup.put("studyDescription", "MRA/v Hd wo");

    for (Entry<String, String> entry : lookup.entrySet()) {
      assertEquals("looking for " + entry.getKey() + " = " + entry.getValue(), entry.getValue(), json.get(entry.getKey()).textValue());
    }
    // Check length of series list
    ArrayNode seriesList = json.withArray("seriesList");
    assertEquals("have series", 2, seriesList.size());

  }
}
