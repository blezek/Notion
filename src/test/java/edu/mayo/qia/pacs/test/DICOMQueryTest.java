package edu.mayo.qia.pacs.test;

import static org.junit.Assert.assertEquals;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.util.List;
import java.util.UUID;

import edu.mayo.qia.pacs.components.Device;
import edu.mayo.qia.pacs.components.Pool;
import edu.mayo.qia.pacs.dicom.DcmQR;
import edu.mayo.qia.pacs.dicom.TagLoader;

@RunWith(SpringJUnit4ClassRunner.class)
public class DICOMQueryTest extends PACSTest {

  @Test
  public void query() throws Exception {

    UUID uid = UUID.randomUUID();
    String aet = uid.toString().substring(0, 10);
    Pool pool = new Pool(aet, aet, aet, false);
    pool = createPool(pool);
    Device device = new Device(".*", ".*", 1234, pool);
    device = createDevice(device);

    List<File> testSeries = sendDICOM(aet, aet, "TOF/*001.dcm");
    DicomObject tags = TagLoader.loadTags(testSeries.get(0));

    DcmQR dcmQR = new DcmQR();
    dcmQR.setRemoteHost("localhost");
    dcmQR.setRemotePort(DICOMPort);
    dcmQR.setCalledAET(aet);
    dcmQR.setCalling(aet);
    dcmQR.open();

    DicomObject response = dcmQR.query();
    dcmQR.close();

    logger.info("Got response: " + response);
    for (String tag : new String[] { "AccessionNumber", "PatientName", "PatientID", "StudyInstanceUID" }) {
      assertEquals(tag, tags.getString(Tag.forName(tag)), response.getString(Tag.forName(tag)));
    }

    assertEquals("NumberOfStudyRelatedSeries", 2, response.getInt(Tag.NumberOfStudyRelatedSeries));
    assertEquals("NumberOfStudyRelatedInstances", testSeries.size(), response.getInt(Tag.NumberOfStudyRelatedInstances));

  }
}
