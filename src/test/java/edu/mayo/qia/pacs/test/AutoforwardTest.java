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

@RunWith(SpringJUnit4ClassRunner.class)
public class AutoforwardTest extends PACSTest {

  @Autowired
  Anonymizer anonymizer;

  @Autowired
  PoolManager poolManager;

  @Test
  public void anonymizeBasics() throws Exception {

    String aet;

    // Autoforward pool
    aet = UUID.randomUUID().toString().substring(0, 10);
    Pool autoforwardPool = new Pool(aet, aet, aet, false);
    autoforwardPool = createPool(autoforwardPool);
    createDevice(new Device(".*", ".*", 1234, autoforwardPool));

    aet = UUID.randomUUID().toString().substring(0, 10);
    Pool pool = new Pool(aet, aet, aet, true);
    pool = createPool(pool);
    Device device = new Device(".*", ".*", 1234, pool);
    device = createDevice(device);
    Device autoforwardDevice = new Device(autoforwardPool.applicationEntityTitle, "localhost", autoforwardPool.getPort(), pool);
    autoforwardDevice.isAutoforward = true;
    autoforwardDevice = createDevice(autoforwardDevice);

    String accessionNumber = "AccessionNumber-1234";
    String patientName = "PN-1234";
    String patientID = "MRA-0068-MRA-0068";
    String script = "var tags = {AccessionNumber: '" + accessionNumber + "', PatientName: '" + patientName + "', PatientID: '" + patientID + "' }; tags;";
    createScript(new Script(pool, script));
    List<File> testSeries = sendDICOM(aet, aet, "TOF/IMAGE001.dcm");

    poolManager.getContainer(pool.poolKey).processAutoForward();

    DcmQR dcmQR = new DcmQR();
    dcmQR.setRemoteHost("localhost");
    dcmQR.setRemotePort(DICOMPort);
    dcmQR.setCalledAET(autoforwardPool.applicationEntityTitle);
    dcmQR.setCalling(pool.applicationEntityTitle);
    dcmQR.open();

    DicomObject response = dcmQR.query();
    dcmQR.close();

    logger.info("Got response: " + response);
    assertTrue("Response was null", response != null);
    assertEquals("AccessionNumber", accessionNumber, response.getString(Tag.AccessionNumber));
    assertEquals("PatientName", patientName, response.getString(Tag.PatientName));
    assertEquals("NumberOfStudyRelatedSeries", 1, response.getInt(Tag.NumberOfStudyRelatedSeries));
    assertEquals("NumberOfStudyRelatedInstances", testSeries.size(), response.getInt(Tag.NumberOfStudyRelatedInstances));

  }
}