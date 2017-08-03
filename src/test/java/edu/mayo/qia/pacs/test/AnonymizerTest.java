package edu.mayo.qia.pacs.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.util.List;
import java.util.UUID;

import edu.mayo.qia.pacs.components.Device;
import edu.mayo.qia.pacs.components.Pool;
import edu.mayo.qia.pacs.components.PoolManager;
import edu.mayo.qia.pacs.components.Script;
import edu.mayo.qia.pacs.ctp.Anonymizer;
import edu.mayo.qia.pacs.dicom.DcmQR;
import edu.mayo.qia.pacs.dicom.TagLoader;

@RunWith(SpringJUnit4ClassRunner.class)
public class AnonymizerTest extends PACSTest {

  @Autowired
  Anonymizer anonymizer;

  @Autowired
  PoolManager poolManager;

  @Test
  public void anonymizeBasics() throws Exception {

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

    // Find the image on the filesystem and ensure the IssuerOfPatientID exists
    // and is correct
    List<String> filePaths = template.queryForList("select FilePath from INSTANCE, SERIES, STUDY where INSTANCE.SeriesKey = SERIES.SeriesKey and SERIES.StudyKey = STUDY.StudyKey and STUDY.PoolKey = ? ", new Object[] { pool.poolKey }, String.class);
    File poolDirectory = poolManager.getContainer(pool.poolKey).getPoolDirectory();
    for (String path : filePaths) {
      File imageFile = new File(poolDirectory, path);
      assertTrue("File exists", imageFile.exists());
      DicomObject tags = TagLoader.loadTags(imageFile);
      assertEquals("", "Notion-" + pool.poolKey, tags.getString(Tag.IssuerOfPatientID));
    }

  }

  @Test
  public void noAnonymizer() throws Exception {

    UUID uid = UUID.randomUUID();
    String aet = uid.toString().substring(0, 10);
    Pool pool = new Pool(aet, aet, aet, false);
    pool = createPool(pool);
    Device device = new Device(".*", ".*", 1234, pool);
    device = createDevice(device);

    List<File> testSeries = sendDICOM(aet, aet, "TOF/IMAGE001.dcm");
    DicomObject dcm = TagLoader.loadTags(testSeries.get(0));

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
    assertEquals("AccessionNumber", dcm.getString(Tag.AccessionNumber), response.getString(Tag.AccessionNumber));
    assertEquals("PatientName", dcm.getString(Tag.PatientName), response.getString(Tag.PatientName));
    assertEquals("PatientID", dcm.getString(Tag.PatientID), response.getString(Tag.PatientID));
    assertEquals("NumberOfStudyRelatedSeries", 1, response.getInt(Tag.NumberOfStudyRelatedSeries));
    assertEquals("NumberOfStudyRelatedInstances", testSeries.size(), response.getInt(Tag.NumberOfStudyRelatedInstances));

  }

  @Test
  public void sequences() throws Exception {
    UUID uid = UUID.randomUUID();
    String aet = uid.toString().substring(0, 10);
    Pool pool = new Pool(aet, aet, aet, false);
    pool = createPool(pool);

    anonymizer.setPool(pool);

    assertEquals("Unset value", null, anonymizer.lookup("PatientID", "Jones"));
    anonymizer.setValue("PatientID", "Jones", "1234");
    assertEquals("Set value", "1234", anonymizer.lookup("PatientID", "Jones"));
    assertEquals("Sequence", 1, anonymizer.sequenceNumber("PatientID", "Jones"));
    assertEquals("Asked for same sequence number", 1, anonymizer.sequenceNumber("PatientID", "Jones"));
    assertEquals("Should increment sequence number", 2, anonymizer.sequenceNumber("PatientID", "Smith"));
  }

  @Test
  public void simpleLookup() throws Exception {
    String script;
    UUID uid = UUID.randomUUID();
    String aet = uid.toString().substring(0, 10);
    Pool pool = new Pool(aet, aet, aet, true);
    pool = createPool(pool);
    Device device = new Device(".*", ".*", 1234, pool);
    device = createDevice(device);

    List<DicomObject> tagList = getTags("TOF/IMAGE001.dcm");
    DicomObject tags = tagList.get(0);
    anonymizer.setPool(pool);
    String patientName = "Noone";
    String patientID = "10";
    anonymizer.setValue("PatientID", tags.getString(Tag.PatientID), patientID);
    anonymizer.setValue("PatientName", tags.getString(Tag.PatientName), patientName);

    // @formatter:off
    script = "anonymizer.info ( 'starting to anonymize' )\n" 
        + "var pn = anonymizer.lookup ( 'PatientName', tags.PatientName)\n"
        + "if ( ! pn ) { \n"
        + "  anonymizer.info ( 'did not find an entry' )\n"
        + "  // Generate a new name using a sequence\n"
        + "  pn = 'Patient-' + anonymizer.sequenceNumber ( 'PatientName', tags.PatientName )\n"
        + "  anonymizer.setValue ( 'PatientName', tags.PatientName, pn )\n"
        + "}\n"
        + "var tags = { PatientName: pn, PatientID: pn }; \n"
        + "tags;\n";
    createScript(new Script(pool,script));
    // @formatter:on

    List<File> testSeries = sendDICOM(aet, aet, "TOF/IMAGE001.dcm");
    testSeries.addAll(sendDICOM(aet, aet, "TOF/MIP00001.dcm"));

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
    assertEquals("PatientName", patientName, response.getString(Tag.PatientName));
    assertEquals("NumberOfStudyRelatedSeries", 2, response.getInt(Tag.NumberOfStudyRelatedSeries));
    assertEquals("NumberOfStudyRelatedInstances", testSeries.size(), response.getInt(Tag.NumberOfStudyRelatedInstances));

  }

  @Test
  public void missingTag() throws Exception {
    UUID uid = UUID.randomUUID();
    String aet = uid.toString().substring(0, 10);
    Pool pool = new Pool(aet, aet, aet, true);
    pool = createPool(pool);
    Device device = new Device(".*", ".*", 1234, pool);
    device = createDevice(device);

    List<DicomObject> tagList = getTags("TOF/IMAGE001.dcm");
    DicomObject tags = tagList.get(0);
    anonymizer.setPool(pool);
    createScript(new Script(pool, "var tags = {SeriesDescription: tags.SeriesDescription}; tags;"));

    List<File> testSeries = sendDICOM(aet, aet, "TOF/*001.dcm");

    DcmQR dcmQR = new DcmQR();
    dcmQR.setRemoteHost("localhost");
    dcmQR.setRemotePort(DICOMPort);
    dcmQR.setCalledAET(aet);
    dcmQR.setCalling(aet);
    dcmQR.open();
    // Ask for StudyDescription back
    dcmQR.addMatchingKey(Tag.toTagPath("StudyDescription"), null);
    DicomObject response = dcmQR.query();
    dcmQR.close();

    logger.info("Got response: " + response);
    assertTrue("Response was null", response != null);
    assertEquals("StudyDescription", tags.getString(Tag.StudyDescription), response.getString(Tag.StudyDescription));
    assertEquals("NumberOfStudyRelatedSeries", 2, response.getInt(Tag.NumberOfStudyRelatedSeries));
    assertEquals("NumberOfStudyRelatedInstances", testSeries.size(), response.getInt(Tag.NumberOfStudyRelatedInstances));
  }

  @Test
  public void noAccessionNumber() throws Exception {

    UUID uid = UUID.randomUUID();
    String aet = uid.toString().substring(0, 10);
    Pool pool = new Pool(aet, aet, aet, true);
    pool = createPool(pool);
    Device device = new Device(".*", ".*", 1234, pool);
    device = createDevice(device);

    List<File> testSeries = sendDICOM(aet, aet, "CTE/*.dcm");

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
    assertTrue("AccessionNumber", response.contains(Tag.AccessionNumber));
    assertEquals("NumberOfStudyRelatedSeries", 1, response.getInt(Tag.NumberOfStudyRelatedSeries));
    assertEquals("NumberOfStudyRelatedInstances", testSeries.size(), response.getInt(Tag.NumberOfStudyRelatedInstances));

    // Find the image on the filesystem and ensure the IssuerOfPatientID exists
    // and is correct
    List<String> filePaths = template.queryForList("select FilePath from INSTANCE, SERIES, STUDY where INSTANCE.SeriesKey = SERIES.SeriesKey and SERIES.StudyKey = STUDY.StudyKey and STUDY.PoolKey = ? ", new Object[] { pool.poolKey }, String.class);
    File poolDirectory = poolManager.getContainer(pool.poolKey).getPoolDirectory();
    String an = null;
    for (String path : filePaths) {
      File imageFile = new File(poolDirectory, path);
      assertTrue("File exists", imageFile.exists());
      DicomObject tags = TagLoader.loadTags(imageFile);
      if (an == null) {
        an = tags.getString(Tag.AccessionNumber);
      }
      assertEquals("Accession number", an, tags.getString(Tag.AccessionNumber));
    }

  }

  @Test
  public void customUUID() throws Exception {

    UUID uid = UUID.randomUUID();
    String aet = uid.toString().substring(0, 10);
    Pool pool = new Pool(aet, aet, aet, true);
    pool = createPool(pool);
    Device device = new Device(".*", ".*", 1234, pool);
    device = createDevice(device);

    sendDICOM(aet, aet, "CTE/*.dcm");

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
    // Expected value is the UID prefix (1.2.840.113713.17.) + the pool key +
    // the MD5 hash of the original StudyInstanceUID
    String expectedStudyInstanceUID = "1.2.840.113713.17." + pool.poolKey + ".14967650384527130252360958094476363837";
    assertEquals("Anonymized StudyInstanceUID", expectedStudyInstanceUID, response.getString(Tag.StudyInstanceUID));

  }

}
