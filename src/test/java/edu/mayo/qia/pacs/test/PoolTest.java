package edu.mayo.qia.pacs.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.representation.Form;

import org.apache.log4j.Logger;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.net.ConfigurationException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import edu.mayo.qia.pacs.components.Device;
import edu.mayo.qia.pacs.components.Pool;
import edu.mayo.qia.pacs.components.PoolContainer;
import edu.mayo.qia.pacs.components.PoolManager;
import edu.mayo.qia.pacs.components.Script;
import edu.mayo.qia.pacs.dicom.DcmQR;
import edu.mayo.qia.pacs.dicom.TagLoader;

@Component
@RunWith(SpringJUnit4ClassRunner.class)
public class PoolTest extends PACSTest {
  static Logger logger = Logger.getLogger(PoolTest.class);

  @Autowired
  PoolManager poolManager;

  @Test
  public void listPools() {
    ClientResponse response = null;
    URI uri = UriBuilder.fromUri(baseUri).path("/pool").build();

    Pool pool = new Pool("list-empty", "list-empty", "list-empty", false);
    response = client.resource(uri).type(JSON).accept(JSON).post(ClientResponse.class, pool);
    assertEquals("Got result", 200, response.getStatus());

    logger.debug("Loading: " + uri);
    response = client.resource(uri).accept(JSON).get(ClientResponse.class);
    assertEquals("Got result", 200, response.getStatus());
    ObjectNode result = response.getEntity(ObjectNode.class);
    assertTrue(result.withArray("pool").size() > 0);

    ArrayNode pools = result.withArray("pool");
    assertTrue("Has port value", pools.get(0).has("port"));
  }

  @Test
  public void createPool() {
    // CURL Code
    /* curl -X POST -H "Content-Type: application/json" -d
     * '{"name":"foo","path":"bar"}' http://localhost:11118/pool */
    ClientResponse response = null;
    URI uri = UriBuilder.fromUri(baseUri).path("/pool").build();
    Pool pool = new Pool("empty", "empty", "empty", false);
    response = client.resource(uri).type(JSON).accept(JSON).post(ClientResponse.class, pool);
    assertEquals("Got result", 200, response.getStatus());
    pool = response.getEntity(Pool.class);
    logger.info("Entity back: " + pool);
    assertTrue("Assigned an id", pool.poolKey != 0);

    // Pool should have 2 scripts attached
    uri = UriBuilder.fromUri(baseUri).path("/pool/").path("" + pool.poolKey).path("/script").build();
    response = client.resource(uri).accept(JSON).get(ClientResponse.class);
    assertEquals("Got result", 200, response.getStatus());
  }

  @Test
  public void invalidAETitle() {
    ClientResponse response = null;
    URI uri = UriBuilder.fromUri(baseUri).path("/pool").build();
    for (String name : new String[] { "no spaces", "no !", "{", "#", "thisiswaytoolongofaname_you_think" }) {
      Pool pool = new Pool("garf", "empty", name, false);
      response = client.resource(uri).type(JSON).accept(JSON).post(ClientResponse.class, pool);
      assertEquals("Got result", Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
    }
  }

  @Test
  public void deletePool() throws Exception {
    UUID uid = UUID.randomUUID();
    String aet = uid.toString().substring(0, 10);
    Pool pool = new Pool(aet, aet, aet, false);
    pool = createPool(pool);
    Device device = new Device(".*", ".*", 1234, pool);
    device = createDevice(device);

    sendDICOM(aet, aet, "TOF/*001.dcm");

    List<Integer> studyKeys = template.queryForList("select StudyKey from STUDY where STUDY.PoolKey = ? ", new Object[] { pool.poolKey }, Integer.class);
    assertEquals("Study", 1, studyKeys.size());
    List<Integer> seriesKeys = template.queryForList("select SERIES.SeriesKey from SERIES, STUDY where SERIES.StudyKey = STUDY.StudyKey and STUDY.PoolKey = ? ", new Object[] { pool.poolKey }, Integer.class);
    assertEquals("Series", 2, seriesKeys.size());
    List<Integer> instanceKeys = template.queryForList("select InstanceKey from INSTANCE, SERIES, STUDY where INSTANCE.SeriesKey = SERIES.SeriesKey and SERIES.StudyKey = STUDY.StudyKey and STUDY.PoolKey = ? ", new Object[] { pool.poolKey }, Integer.class);
    assertEquals("Instance", 2, instanceKeys.size());
    List<String> filePaths = template.queryForList("select FilePath from INSTANCE, SERIES, STUDY where INSTANCE.SeriesKey = SERIES.SeriesKey and SERIES.StudyKey = STUDY.StudyKey and STUDY.PoolKey = ? ", new Object[] { pool.poolKey }, String.class);
    assertEquals("Files", 2, filePaths.size());
    assertEquals("Device", new Integer(1), template.queryForObject("select count(*) from DEVICE where PoolKey = " + pool.poolKey, Integer.class));
    assertEquals("Script", new Integer(1), template.queryForObject("select count(*) from SCRIPT where PoolKey = " + pool.poolKey, Integer.class));

    // Create
    URI uri = UriBuilder.fromUri(baseUri).path("/pool/" + pool.poolKey + "/lookup").path("create").build();
    Form form = new Form();
    form.add("Type", "PatientName");
    form.add("Name", "Mr. Magoo");
    form.add("Value", "Rikki-tikki-tavvi");
    ClientResponse response = client.resource(uri).post(ClientResponse.class, form);
    assertEquals("Status", 200, response.getStatus());
    assertEquals("Lookup", new Integer(1), template.queryForObject("select count(*) from LOOKUP where PoolKey = " + pool.poolKey, Integer.class));

    File poolDirectory = poolManager.getContainer(pool.poolKey).getPoolDirectory();
    poolManager.deletePool(pool);

    assertEquals("Instance", new Integer(0), template.queryForObject("select count(*) from INSTANCE, SERIES, STUDY where INSTANCE.SeriesKey = SERIES.SeriesKey and SERIES.StudyKey = STUDY.StudyKey and STUDY.PoolKey = " + pool.poolKey, Integer.class));
    assertEquals("Series", new Integer(0), template.queryForObject("select count(*) from SERIES, STUDY where SERIES.StudyKey = STUDY.StudyKey and STUDY.PoolKey = " + pool.poolKey, Integer.class));
    assertEquals("Study", new Integer(0), template.queryForObject("select count(*) from STUDY where STUDY.PoolKey = " + pool.poolKey, Integer.class));
    assertEquals("Pool", new Integer(0), template.queryForObject("select count(*) from POOL where PoolKey = " + pool.poolKey, Integer.class));
    assertEquals("Device", new Integer(0), template.queryForObject("select count(*) from DEVICE where PoolKey = " + pool.poolKey, Integer.class));
    assertEquals("Lookup", new Integer(0), template.queryForObject("select count(*) from LOOKUP where PoolKey = " + pool.poolKey, Integer.class));
    assertEquals("Script", new Integer(0), template.queryForObject("select count(*) from SCRIPT where PoolKey = " + pool.poolKey, Integer.class));
    for (String filePath : filePaths) {
      File file = new File(poolDirectory, filePath);
      assertFalse("File: " + file, file.exists());
    }
    assertNull("Manager", poolManager.getContainer(pool.poolKey));
  }

  @Test
  public void updateOnNewSeries() throws Exception {

    UUID uid = UUID.randomUUID();
    String aet = uid.toString().substring(0, 10);
    Pool pool = new Pool(aet, aet, aet, true);
    pool = createPool(pool);
    Device device = new Device(".*", ".*", 1234, pool);
    device = createDevice(device);

    String accessionNumber = "AccessionNumber-1234";
    String patientName = "PN-1234";
    String patientID = "MRA-0068-MRA-0068";

    String script = "var tags = {AccessionNumber: '" + accessionNumber + "', PatientName: '" + patientName + "', PatientID: '" + patientID + "' }; tags;";
    createScript(new Script(pool, script));

    List<File> testSeries = sendDICOM(aet, aet, "TOF/IMAGE001.dcm");

    DcmQR dcmQR = new DcmQR();
    dcmQR.setRemoteHost("localhost");
    dcmQR.setRemotePort(DICOMPort);
    dcmQR.setCalledAET(aet);
    dcmQR.setCalling(aet);
    dcmQR.open();

    DicomObject response = dcmQR.query();
    dcmQR.close();

    logger.info("Got response: " + response);
    assertTrue("Response was null", response != null);
    assertEquals("AccessionNumber", accessionNumber, response.getString(Tag.AccessionNumber));
    assertEquals("PatientName", patientName, response.getString(Tag.PatientName));
    assertEquals("NumberOfStudyRelatedSeries", 1, response.getInt(Tag.NumberOfStudyRelatedSeries));
    assertEquals("NumberOfStudyRelatedInstances", testSeries.size(), response.getInt(Tag.NumberOfStudyRelatedInstances));

    script = "var tags = {AccessionNumber: '42', PatientName: 'Gone', PatientID: '" + patientID + "' }; tags;";
    createScript(new Script(pool, script));

    // Send again and query
    testSeries = sendDICOM(aet, aet, "TOF/IMAGE001.dcm");
    dcmQR = new DcmQR();
    dcmQR.setRemoteHost("localhost");
    dcmQR.setRemotePort(DICOMPort);
    dcmQR.setCalledAET(aet);
    dcmQR.setCalling(aet);
    dcmQR.open();

    response = dcmQR.query();
    dcmQR.close();
    logger.info("Got response: " + response);
    assertTrue("Response was null", response != null);
    assertEquals("AccessionNumber", "42", response.getString(Tag.AccessionNumber));
    assertEquals("PatientName", "Gone", response.getString(Tag.PatientName));
    assertEquals("NumberOfStudyRelatedSeries", 1, response.getInt(Tag.NumberOfStudyRelatedSeries));
    assertEquals("NumberOfStudyRelatedInstances", testSeries.size(), response.getInt(Tag.NumberOfStudyRelatedInstances));
  }

  @Test
  public void updateAccessionNumber() throws IOException, ConfigurationException, InterruptedException {
    UUID uid = UUID.randomUUID();
    String aet = uid.toString().substring(0, 10);
    Pool pool = new Pool(aet, aet, aet, true);
    pool = createPool(pool);
    Device device = new Device(".*", ".*", 1234, pool);
    PoolContainer container = poolManager.getContainer(pool.poolKey);

    device = createDevice(device);

    sendDICOM(aet, aet, "TOF/*001.dcm");

    // Change the anonymizer and continue
    String script = "var tags = {AccessionNumber: '42', PatientName: 'Gone', PatientID: '1234' }; tags;";
    createScript(new Script(pool, script));

    sendDICOM(aet, aet, "TOF/*001.dcm");

    // See how many series we have, and check the images on disk
    List<String> filePaths = template.queryForList("select FilePath from INSTANCE, SERIES, STUDY where INSTANCE.SeriesKey = SERIES.SeriesKey and SERIES.StudyKey = STUDY.StudyKey and STUDY.PoolKey = ?", new Object[] { pool.poolKey }, String.class);
    for (String path : filePaths) {
      assertTrue("File exists " + path, new File(container.getPoolDirectory(), path).exists());
      // See if it has the new accession number
      DicomObject tags = TagLoader.loadTags(new File(container.getPoolDirectory(), path));
      assertTrue("File: " + path + " has AccessionNumber", tags.contains(Tag.AccessionNumber));
      assertEquals("File: " + path + " has AccessionNumber == 42", "42", tags.getString(Tag.AccessionNumber));
    }

  }

}
