package edu.mayo.qia.pacs.components;

import org.apache.log4j.Logger;
import org.dcm4che2.net.Association;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import edu.mayo.qia.pacs.Notion;
import edu.mayo.qia.pacs.dicom.DICOMReceiver;
import io.dropwizard.lifecycle.Managed;

/**
 * Manages the pools, starting them up, shutdown, etc.
 * 
 * @author Daniel Blezek
 * 
 */
@Component
public class PoolManager implements Managed {
  static Logger logger = Logger.getLogger(PoolManager.class);

  static ConcurrentMap<String, PoolContainer> poolContainers = new ConcurrentHashMap<String, PoolContainer>();

  @Autowired
  JdbcTemplate template;

  @Autowired
  TaskScheduler taskScheduler;

  @Autowired
  SessionFactory sessionFactory;

  @Autowired
  DICOMReceiver dicomReceiver;

  @SuppressWarnings("unchecked")
  @Override
  public void start() {
    final Session session = sessionFactory.openSession();
    for (Pool pool : (List<Pool>) session.createQuery("from Pool").list()) {
      newPool(pool);
    }
    session.close();
  }

  public void newPool(Pool pool) {
    PoolContainer poolContainer = Notion.context.getBean(PoolContainer.class);
    poolContainers.put(pool.applicationEntityTitle, poolContainer);
    logger.debug("Starting pool: " + pool);
    poolContainer.start(pool);

  }

  @Override
  public void stop() {
    for (PoolContainer poolContainer : poolContainers.values()) {
      poolContainer.stop();
    }
  }

  public PoolContainer getContainer(String aet) {
    return poolContainers.get(aet);
  }

  public PoolContainer getContainer(int poolKey) {
    for (PoolContainer container : poolContainers.values()) {
      if (container.pool.poolKey == poolKey) {
        return container;
      }
    }
    return null;
  }

  public void deletePool(Pool pool) {
    PoolContainer container = getContainer(pool.applicationEntityTitle);
    container.stop();
    poolContainers.remove(pool.applicationEntityTitle, container);
    container.delete();
  }

  /**
   * Handle a request to create an image, NB: must only handle one request at a
   * time.
   */
  public void processIncomingFile(Association association, File incoming) throws Exception {

    // Pull out the filename
    PoolContainer container = poolContainers.get(association.getCalledAET());
    if (container == null) {
      throw new Exception("Did not find containing for the Called AET");
    }

    container.process(incoming, null);
  }

  /** Update the PoolContainer, because the pool may have changed */
  public void update(Pool pool) {
    getContainer(pool.poolKey).update(pool);
  }

  public void remove(PoolContainer poolContainer) {
    boolean v = poolContainers.remove(poolContainer.getPool().applicationEntityTitle, poolContainer);
    if (!v) {
      throw new RuntimeException("Could not remove pool for " + poolContainer.getPool().applicationEntityTitle);
    }
  }

  /**
   * @return the poolContainers
   */
  public static ConcurrentMap<String, PoolContainer> getPoolContainers() {
    return poolContainers;
  }

}
