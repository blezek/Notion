package edu.mayo.qia.pacs.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.dcm4che2.net.ConfigurationException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import edu.mayo.qia.pacs.components.Device;
import edu.mayo.qia.pacs.components.Pool;
import edu.mayo.qia.pacs.components.PoolContainer;
import edu.mayo.qia.pacs.components.PoolManager;

@RunWith(SpringJUnit4ClassRunner.class)
public class DeleteTest extends PACSTest {

  @Autowired
  PoolManager poolManager;

  @Test
  public void delete() throws IOException, ConfigurationException, InterruptedException {
    UUID uid = UUID.randomUUID();
    String aet = uid.toString().substring(0, 10);
    Pool pool = new Pool(aet, aet, aet, false);
    pool = createPool(pool);
    Device device = new Device(".*", ".*", 1234, pool);
    device = createDevice(device);

    List<File> testSeries = sendDICOM(aet, aet, "TOF/*001.dcm");
    PoolContainer container = poolManager.getContainer(pool.poolKey);
    // See how many series we have, and check the images on disk
    List<String> filePaths = template.queryForList("select FilePath from INSTANCE, SERIES, STUDY where INSTANCE.SeriesKey = SERIES.SeriesKey and SERIES.StudyKey = STUDY.StudyKey and STUDY.PoolKey = ?", new Object[] { pool.poolKey }, String.class);
    for (String path : filePaths) {
      assertTrue("File exists " + path, new File(container.getPoolDirectory(), path).exists());
    }
    assertEquals("Number of imported files", testSeries.size(), filePaths.size());
    int studyKey = template.queryForObject("select StudyKey from STUDY where PoolKey = ?", new Object[] { pool.poolKey }, Integer.class);
    container.deleteStudy(studyKey);

    // Are the files deleted?
    for (String path : filePaths) {
      assertFalse("File exists " + path, new File(container.getPoolDirectory(), path).exists());
    }
    // Are the series deleted?
    assertEquals("STUDY", new Integer(0), template.queryForObject("select count(*) from STUDY where PoolKey = ?", new Object[] { pool.poolKey }, Integer.class));
    assertEquals("SERIES", new Integer(0), template.queryForObject("select count(*) from SERIES, STUDY where SERIES.StudyKey = STUDY.StudyKey and STUDY.PoolKey = ?", new Object[] { pool.poolKey }, Integer.class));
    assertEquals("INSTANCE", new Integer(0),
        template.queryForObject("select count(*) from INSTANCE, SERIES, STUDY where INSTANCE.SeriesKey = SERIES.SeriesKey and SERIES.StudyKey = STUDY.StudyKey and STUDY.PoolKey = ?", new Object[] { pool.poolKey }, Integer.class));
  }

}
