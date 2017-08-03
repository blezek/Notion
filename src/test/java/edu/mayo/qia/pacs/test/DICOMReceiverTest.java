package edu.mayo.qia.pacs.test;

import static org.junit.Assert.assertEquals;

import org.dcm4che2.net.ConfigurationException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import edu.mayo.qia.pacs.components.Device;
import edu.mayo.qia.pacs.components.Pool;

@RunWith(SpringJUnit4ClassRunner.class)
public class DICOMReceiverTest extends PACSTest {

  @Test
  public void sendTOF() throws IOException, ConfigurationException, InterruptedException {
    UUID uid = UUID.randomUUID();
    String aet = uid.toString().substring(0, 10);

    Pool pool = new Pool(aet, aet, aet, false);
    pool = createPool(pool);
    Device device = new Device(".*", ".*", 1234, pool);
    device = createDevice(device);

    List<File> testSeries = sendDICOM(aet, aet, "TOF/*001.dcm");

    int studyCount = template.queryForObject("select count(*) from STUDY where PoolKey = ?", new Object[] { pool.poolKey }, Integer.class);
    assertEquals("StudyCount", 1, studyCount);
    int seriesCount = template.queryForObject("select count(Series.SeriesKey) from Study, Series where Study.PoolKey = ? and Series.StudyKey = Study.StudyKey", new Object[] { pool.poolKey }, Integer.class);
    assertEquals("Series Count", 2, seriesCount);
    int instanceCount = template.queryForObject("select count(Instance.InstanceKey) from Instance, Study, Series where Instance.SeriesKey = Series.SeriesKey and Study.PoolKey = ? and Series.StudyKey = Study.StudyKey", new Object[] { pool.poolKey },
        Integer.class);
    assertEquals("Instance Count", testSeries.size(), instanceCount);

  }
}
