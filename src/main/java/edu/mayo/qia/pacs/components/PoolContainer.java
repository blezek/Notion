package edu.mayo.qia.pacs.components;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.codahale.metrics.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.dcm4che.dict.Tags;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.ctp.pipeline.Processor;
import org.rsna.ctp.stdstages.DicomAnonymizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;
import org.trimou.Mustache;
import org.trimou.engine.MustacheEngine;
import org.trimou.engine.MustacheEngineBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.Files;

import edu.mayo.qia.pacs.Audit;
import edu.mayo.qia.pacs.Notion;
import edu.mayo.qia.pacs.NotionConfiguration;
import edu.mayo.qia.pacs.ctp.Anonymizer;
import edu.mayo.qia.pacs.dicom.DcmSnd;
import edu.mayo.qia.pacs.dicom.TagLoader;
import edu.mayo.qia.pacs.metric.RateGauge;

/**
 * Manage a particular pool.
 * 
 * @author Daniel Blezek
 * 
 */
@Component
@Scope("prototype")
public class PoolContainer {
  static Logger logger = Logger.getLogger(PoolContainer.class);
  static Timer moveTimer = Notion.metrics.timer(MetricRegistry.name("Pool", "move", "timer"));
  static Counter moveCounter = Notion.metrics.counter("Pool.move.count");
  static Timer processTimer = Notion.metrics.timer("Pool.process.timer");
  RateGauge imagesProcessedPerSecond = new RateGauge();
  RateGauge imagesMovedPerSecond = new RateGauge();

  // Pool versions
  Timer poolMoveTimer;
  Counter poolMoveCounter;
  Timer poolProcessTimer;
  Meter poolReceiveMeter;

  public static final int[] SortedFilename = { Tags.AccessionNumber, Tags.StudyInstanceUID, Tags.SeriesInstanceUID, Tags.SOPInstanceUID };

  volatile Pool pool;
  File poolDirectory;
  File quarantinesDirectory;
  File scriptsDirectory;
  File incomingDirectory;
  File imageDirectory;
  ConcurrentLinkedQueue<File> instancesToForward = new ConcurrentLinkedQueue<File>();
  PipelineStage ctpAnonymizer = null;

  @Autowired
  private JdbcTemplate template;

  @Autowired
  SessionFactory sessionFactory;

  @Autowired
  PoolManager poolManager;

  @Autowired
  NotionConfiguration configuration;

  @Autowired
  ObjectMapper objectMapper;

  private String sequenceName;

  public PoolContainer() {
  }

  public void start(final Pool pool) {
    this.pool = pool;
    if (this.pool.poolKey <= 0) {
      throw new RuntimeException("PoolKey must be set!");
    }
    logger.debug("Starting up pool: " + pool);

    // Do we have a sequence for this pool?
    this.sequenceName = "UID" + pool.poolKey;
    boolean sequenceExists = false;
    try {
      template.queryForObject("VALUES SYSCS_UTIL.SYSCS_PEEK_AT_SEQUENCE('APP', ?)", Integer.class, this.sequenceName);
      sequenceExists = true;
    } catch (Exception e) {
      logger.error("Did not find sequence: " + this.sequenceName);
    }
    try {
      if (!sequenceExists) {
        template.update("create sequence " + sequenceName + " AS INT START WITH 1");
      }
    } catch (Exception e) {
      logger.error("Error creating sequence", e);
    }

    // See if the directory exists
    File poolBase = new File(configuration.notion.imageDirectory, "ImageStorage");
    poolDirectory = new File(poolBase, Integer.toString(pool.poolKey));
    if (!poolDirectory.exists()) {
      poolDirectory.mkdirs();
    }
    quarantinesDirectory = new File(poolDirectory, "quarantines");
    quarantinesDirectory.mkdirs();
    incomingDirectory = new File(poolDirectory, "incoming");
    incomingDirectory.mkdirs();
    incomingDirectory = new File(poolDirectory, "incoming");
    incomingDirectory.mkdirs();
    scriptsDirectory = new File(poolDirectory, "scripts");
    scriptsDirectory.mkdirs();
    // Would start CTP here as well
    configureCTP();

    // Pool-specific metrics
    poolMoveTimer = Notion.metrics.timer(MetricRegistry.name("Pool", pool.applicationEntityTitle, "move", "timer"));
    poolMoveCounter = Notion.metrics.counter(MetricRegistry.name("Pool", pool.applicationEntityTitle, "move", "count"));
    poolProcessTimer = Notion.metrics.timer(MetricRegistry.name("Pool", pool.applicationEntityTitle, "process", "timer"));
    poolReceiveMeter = Notion.metrics.meter(MetricRegistry.name("Pool", pool.applicationEntityTitle, "process", "meter"));
    Notion.metrics.register(MetricRegistry.name("Pool", pool.applicationEntityTitle, "process", "rate"), imagesProcessedPerSecond);
    Notion.metrics.register(MetricRegistry.name("Pool", pool.applicationEntityTitle, "move", "rate"), imagesMovedPerSecond);

    final Map<String, String> queryMap = new HashMap<String, String>();
    queryMap.put("instances", "select count(*) from INSTANCE, SERIES, STUDY where STUDY.StudyKey = SERIES.StudyKey and SERIES.SeriesKey = INSTANCE.SeriesKey and STUDY.PoolKey = ?");
    queryMap.put("series", "select count(*) from SERIES, STUDY where STUDY.StudyKey = SERIES.StudyKey and STUDY.PoolKey = ?");
    queryMap.put("studies", "select count(*) from STUDY where STUDY.PoolKey = ?");

    for (final String table : queryMap.keySet()) {
      Notion.metrics.register(MetricRegistry.name("Pool", pool.applicationEntityTitle, table.toLowerCase()), new CachedGauge<Long>(5, TimeUnit.MINUTES) {
        @Override
        protected Long loadValue() {
          return template.queryForObject(queryMap.get(table), Long.class, pool.poolKey);
        }
      });
    }

  }

  public FileObject executePipeline(FileObject fileObject) throws Exception {
    if (pool.anonymize) {
      // Execute all the stages
      // 1. Prepare the Javascript anonymizer
      // 2. Execute the CTP anonymizer
      // 3. Execute the Javascript anonymizer
      // Load the tags, replace PatientName, PatientID and AccessionNumber
      DicomObject originalTags = TagLoader.loadTags(fileObject.getFile());

      FileObject stageOne = ((Processor) ctpAnonymizer).process(fileObject);
      fileObject = Anonymizer.process(this, stageOne, originalTags);
    }
    return fileObject;
  }

  public void configureCTP() {

    DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder docBuilder = null;
    try {
      docBuilder = docFactory.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      logger.error("Failed to construct an XML builder", e);
      return;
    }

    // root elements
    Document doc = docBuilder.newDocument();
    Element element;
    element = doc.createElement("DicomAnonymizer");
    element.setAttribute("name", "DicomAnonymizer");
    element.setAttribute("root", new File(poolDirectory, "anonymizer").getAbsolutePath());
    File script = new File(scriptsDirectory, "anonymizer.script");

    // Create the script
    if (!script.exists()) {
      try {
        // Read the template from resources and run
        ClassPathResource resource = new ClassPathResource("ctp/anonymizer.script");
        String template = IOUtils.toString(resource.getInputStream());

        MustacheEngine engine = MustacheEngineBuilder.newBuilder().build();
        Mustache mustache = engine.compileMustache("anonymizer", template);
        try (FileWriter out = new FileWriter(script);) {
          mustache.render(out, pool);
        }

        // IOUtils.copy(resource.getInputStream(), new
        // FileOutputStream(script));
      } catch (Exception e) {
        logger.error("Error copying the anonymizer script", e);
      }
    }
    element.setAttribute("script", script.getAbsolutePath());
    ctpAnonymizer = new DicomAnonymizer(element);
    ctpAnonymizer.start();
  }

  public String getCTPConfig() throws Exception {
    File script = new File(scriptsDirectory, "anonymizer.script");
    return IOUtils.toString(new FileInputStream(script));
  }

  public void putCTPConfig(String text) throws Exception {
    File script = new File(scriptsDirectory, "anonymizer.script");
    FileOutputStream fos = new FileOutputStream(script);
    try {
      IOUtils.write(text, fos);
    } finally {
      IOUtils.closeQuietly(fos);
    }
  }

  public void deleteStudy(String studyInstanceUID) {
    int count = template.queryForObject("select count(StudyKey) from STUDY where PoolKey = ? and StudyInstanceUID = ?", new Object[] { pool.poolKey, studyInstanceUID }, Integer.class);
    if (count > 0) {
      Integer studyKey = template.queryForObject("select StudyKey from STUDY where PoolKey = ? and StudyInstanceUID = ?", new Object[] { pool.poolKey, studyInstanceUID }, Integer.class);
      if (studyKey != null) {
        deleteStudy(studyKey);
      }
    }
  }

  public boolean deleteStudy(final int studyKey) {
    Integer count = template.queryForObject("select count(*) from STUDY where PoolKey = ? and StudyKey = ?", new Object[] { pool.poolKey, studyKey }, Integer.class);
    if (count.intValue() != 1) {
      return false;
    }

    // Delete the study, series and instances
    synchronized (this) {
      final File deleteDirectory = new File(poolDirectory, "deleted");
      deleteDirectory.mkdirs();
      final Set<File> directories = new HashSet<File>();

      // Collect all the files to delete
      List<String> filePaths = template.queryForList("select FilePath from INSTANCE, SERIES, STUDY where INSTANCE.SeriesKey = SERIES.SeriesKey and SERIES.StudyKey = STUDY.StudyKey and STUDY.PoolKey = ? and STUDY.StudyKey = ?", new Object[] { pool.poolKey,
          studyKey }, String.class);
      // Delete, should cascade!
      template.update("delete from STUDY where PoolKey = ? and StudyKey = ?", pool.poolKey, studyKey);

      logger.error("\n\n\t=====\n\n\tDeleting STUDY " + studyKey + " from POOL " + pool.poolKey + "\n\n\t=====\n\n");

      for (String filePath : filePaths) {
        File file = new File(poolDirectory, filePath);
        directories.add(file.getParentFile());
        try {
          FileUtils.moveFile(file, new File(deleteDirectory, UUID.randomUUID().toString()));
        } catch (IOException e) {
          logger.error("Failed to move file", e);
        }
      }

      // Delete the deleted directory
      for (File file : deleteDirectory.listFiles()) {
        if (file.isFile()) {
          file.delete();
        }
      }
      for (File dir : directories) {
        dir.delete();
      }
      return true;
    }
  }

  /**
   * Move a file from a different pool
   * 
   * @throws Exception
   */
  public void importFromPool(File incoming) throws Exception {
    File importFile = new File(incomingDirectory, UUID.randomUUID().toString());
    FileUtils.copyFile(incoming, importFile);
    process(importFile, null);
  }

  String noNulls(String in) {
    return in != null ? in : "";
  }

  public void process(File incoming, MoveStatus status) throws Exception, IOException {
    process(incoming, status, new ProcessCache());
  }

  public void process(File incoming, MoveStatus status, ProcessCache cache) throws Exception {
    synchronized (this) {
      poolReceiveMeter.mark();
      Timer.Context poolContext = poolProcessTimer.time();
      Timer.Context context = processTimer.time();
      // Handle one per container
      // Have the container process!
      DicomObject originalTags = TagLoader.loadTags(incoming);

      org.rsna.ctp.objects.DicomObject fileObject = new org.rsna.ctp.objects.DicomObject(incoming);
      FileObject outObject = this.executePipeline(fileObject);
      File inFile = outObject.getFile();
      File originalFile = incoming;

      // Index the file
      DicomObject tags = TagLoader.loadTags(inFile);
      logger.debug("Saving file for " + tags.getString(Tag.PatientName));

      File relativePath = constructRelativeFileName(tags);

      File outFile = new File(this.poolDirectory, relativePath.getPath());

      if (!inFile.exists()) {
        logger.error("Input file " + inFile + " does not exist");
        throw new Exception("Input file " + inFile + " does not exist");
      }

      logger.debug("Final path: " + outFile);
      outFile.getParentFile().mkdirs();

      // Start a transaction
      /* to debug: select * from syscs_diag.lock_table; select * from
       * syscs_diag.transaction_table; */
      Session session = sessionFactory.openSession();
      session.beginTransaction();

      try {

        // Assume that if the cache has a study, we can just use it. OTW query
        // and update.
        Query query;

        Study study = cache.studies.get(tags.getString(Tag.StudyInstanceUID));
        if (study == null) {

          query = session.createQuery("from Study where PoolKey = :poolkey and StudyInstanceUID = :suid");
          query.setInteger("poolkey", pool.poolKey);
          query.setString("suid", tags.getString(Tag.StudyInstanceUID));
          study = (Study) query.uniqueResult();

          if (study == null) {
            study = new Study(tags);
            study.pool = pool;
            // Log when we get a new study
            Audit.log(pool.toString(), "create_study", tags);
          } else {
            study.update(tags);
            Audit.log(pool.toString(), "update_study", tags);
          }
          session.saveOrUpdate(study);
          cache.studies.put(tags.getString(Tag.StudyInstanceUID), study);
          session.getTransaction().commit();
          session.beginTransaction();
        }

        Series series = cache.series.get(tags.getString(Tag.SeriesInstanceUID));
        if (series == null) {
          // Find the Series
          query = session.createQuery("from Series where StudyKey = :studykey and SeriesInstanceUID = :suid");
          query.setInteger("studykey", study.StudyKey);
          query.setString("suid", tags.getString(Tag.SeriesInstanceUID));
          series = (Series) query.uniqueResult();
          if (series == null) {
            series = new Series(tags);
            series.study = study;
            // Log when we get a new study
            Audit.log(pool.toString(), "create_series", tags);
          } else {
            series.update(tags);
          }
          session.saveOrUpdate(series);
          cache.series.put(tags.getString(Tag.SeriesInstanceUID), series);
          session.getTransaction().commit();
          session.beginTransaction();
        }

        // Find the Instance
        query = session.createQuery("from Instance where SeriesKey = :serieskey and SOPInstanceUID = :suid").setInteger("serieskey", series.SeriesKey);
        query.setString("suid", tags.getString(Tag.SOPInstanceUID));
        Instance instance = (Instance) query.uniqueResult();
        if (instance == null) {
          instance = new Instance(tags, relativePath.getPath());
          instance.series = series;
        } else {
          instance.update(tags);
          // Update the file path, delete the existing file
          File existingFile = new File(this.poolDirectory, instance.FilePath);
          instance.FilePath = relativePath.getPath();
          existingFile.delete();
        }
        session.saveOrUpdate(instance);
        session.getTransaction().commit();
        session.beginTransaction();

        // Copy the file, remove later
        Files.copy(inFile, outFile);
        logger.debug("Moved file " + inFile + " to " + outFile);

        // Queue the file up to be forwarded later
        instancesToForward.add(outFile);

        // Delete the input file, it is not needed any more
        if (inFile.exists()) {
          inFile.delete();
        }
        if (originalFile.exists()) {
          originalFile.delete();
        }
        if (status != null) {
          status.movedStudyKey = study.StudyKey;
        }

        if (pool.anonymize) {

          // Save the association info for later
          template
              .update(
                  "insert into ANONYMIZATIONMAPTEMP ( PoolKey, OriginalPatientName, AnonymizedPatientName, OriginalPatientID, AnonymizedPatientID, OriginalAccessionNumber, AnonymizedAccessionNumber, OriginalPatientBirthDate, AnonymizedPatientBirthDate ) values ( ?, ?, ?, ?, ?, ?, ?, ?, ? )",
                  pool.poolKey, noNulls(originalTags.getString(Tag.PatientName)), noNulls(tags.getString(Tag.PatientName)), noNulls(originalTags.getString(Tag.PatientID)), noNulls(tags.getString(Tag.PatientID)),
                  noNulls(originalTags.getString(Tag.AccessionNumber)), noNulls(tags.getString(Tag.AccessionNumber)), noNulls(originalTags.getString(Tag.PatientBirthDate)), noNulls(tags.getString(Tag.PatientBirthDate)));
        }
      } catch (Exception e) {
        logger.error("Caught exception", e);
      } finally {
        poolContext.stop();
        context.stop();
        session.close();
        imagesProcessedPerSecond.mark();
      }
    }
  }

  public void stop() {
    logger.debug("Shutting down pool: " + pool);
    // Stop all the stages
    ctpAnonymizer.shutdown();

      // Bug fixed by Xiaojiang Yang - Remove all Matching metrics - otherwise, user will not be able to re-create the just-deleted pool:
      Notion.metrics.removeMatching(new MetricFilter() {
                                        @Override
                                        public boolean matches(String name, Metric metric) {
                                            if(name.startsWith( "Pool." + pool.applicationEntityTitle )) {
                                                return true;
                                            }
                                            else{
                                                return false;
                                            }
                                        }
                                    }
                                    );
      //..
  }

  public File getPoolDirectory() {
    return poolDirectory;
  }

  public void delete() {
    stop();
    // First start by deleting all the studies
    template.query("select StudyKey from STUDY where PoolKey = ?", new Object[] { pool.poolKey }, new RowCallbackHandler() {

      @Override
      public void processRow(ResultSet rs) throws SQLException {
        deleteStudy(rs.getInt("StudyKey"));
      }
    });

    // Will cascade to all other tables
    template.update("delete from POOL where PoolKey = ?", pool.poolKey);

    // Delete all the files associated with the container
    try {
      FileUtils.deleteDirectory(poolDirectory);
    } catch (IOException e) {
      logger.error("Failed to delete directory", e);
    }
  }

  public Pool getPool() {
    return pool;
  }

  public void update(Pool pool) {
    this.pool = pool;
  }

  static File constructRelativeFileName(DicomObject dataset) {
    File relativePath = new File("sorted");
    for (int tag : SortedFilename) {
      String t = dataset.getString(tag);
      if (t == null) {
        t = "UNKNOWN";
      }
      // We want our filenames to be A-Z, a-z, 0-9, . and nothing else.
      t = t.replaceAll("[^A-Za-z0-9.]", "");
      relativePath = new File(relativePath, t);
    }
    // Always store with .dcm at the end
    relativePath = new File(relativePath.getParentFile(), relativePath.getName() + ".dcm");
    return relativePath;
  }

  public boolean moveStudyTo(String studyInstanceUID, final PoolContainer destination, final MoveStatus status) {
    final AtomicBoolean successful = new AtomicBoolean(true);
    long numberToMove = template.queryForObject("select count(INSTANCE.FilePath) from INSTANCE, STUDY, SERIES where STUDY.PoolKey = ? AND INSTANCE.SeriesKey = SERIES.SeriesKey and SERIES.StudyKey = STUDY.StudyKey and STUDY.StudyInstanceUID = ?",
        new Object[] { this.pool.poolKey, studyInstanceUID }, Long.class);
    moveCounter.inc(numberToMove);
    poolMoveCounter.inc(numberToMove);
    template.query("select INSTANCE.FilePath from INSTANCE, STUDY, SERIES where STUDY.PoolKey = ? AND INSTANCE.SeriesKey = SERIES.SeriesKey and SERIES.StudyKey = STUDY.StudyKey and STUDY.StudyInstanceUID = ?", new Object[] { this.pool.poolKey,
        studyInstanceUID }, new RowCallbackHandler() {

      @Override
      public void processRow(ResultSet rs) throws SQLException {
        File f = new File(getPoolDirectory(), rs.getString("FilePath"));
        File tempDir = new File(getPoolDirectory(), "incoming");
        tempDir.mkdirs();
        File tmpFile = new File(tempDir, UUID.randomUUID().toString() + ".dcm");
        try {
          Files.copy(f, tmpFile);
          destination.process(tmpFile, status);
        } catch (Exception e) {
          successful.set(false);
          logger.error("Error processing file: " + f + " into pool " + pool, e);
        }
        imagesMovedPerSecond.mark();
        moveCounter.dec();
        poolMoveCounter.dec();
      }
    });
    destination.processAnonymizationMap();
    return successful.get();
  }

  public void processAnonymizationMap() {
    Notion.context.getBean("executor", ExecutorService.class).execute(new Runnable() {

      @Override
      public void run() {
        synchronized (this) {
          template.query("select distinct OriginalPatientName, AnonymizedPatientName, OriginalPatientID, AnonymizedPatientID, OriginalAccessionNumber, AnonymizedAccessionNumber, OriginalPatientBirthDate, AnonymizedPatientBirthDate"
              + " from ANONYMIZATIONMAPTEMP where PoolKey = ?", new Object[] { pool.poolKey }, new RowCallbackHandler() {

            @Override
            public void processRow(ResultSet rs) throws SQLException {
              String opn, apn, opi, api, oan, aan, opbd, apbd;
              opn = rs.getString(1);
              apn = rs.getString(2);
              opi = rs.getString(3);
              api = rs.getString(4);
              oan = rs.getString(5);
              aan = rs.getString(6);
              opbd = rs.getString(7);
              apbd = rs.getString(8);

              ObjectNode node = objectMapper.createObjectNode();
              node.put("OriginalPatientName", opn);
              node.put("AnonymizedPatientName", apn);
              node.put("OriginalPatientID", opi);
              node.put("AnonymizedPatientID", api);
              node.put("OriginalAccessionNumber", oan);
              node.put("AnonymizedAccessionNumber", aan);
              node.put("OriginalPatientBirthDate", opbd);
              node.put("AnonymizedPatientBirthDate", apbd);
              Audit.log(pool.toString(), "anonymization", node);

              int count = template
                  .queryForObject(
                      "select count(*) from ANONYMIZATIONMAP where PoolKey = ? and OriginalPatientName = ? and AnonymizedPatientName = ? and OriginalPatientID = ? and AnonymizedPatientID = ? and OriginalAccessionNumber = ? and AnonymizedAccessionNumber = ? and OriginalPatientBirthDate = ? and AnonymizedPatientBirthDate = ?",
                      new Object[] { pool.poolKey, opn, apn, opi, api, oan, aan, opbd, apbd }, Integer.class);

              if (count == 0) {
                template
                    .update(
                        "insert into ANONYMIZATIONMAP ( PoolKey, OriginalPatientName, AnonymizedPatientName, OriginalPatientID, AnonymizedPatientID, OriginalAccessionNumber, AnonymizedAccessionNumber, OriginalPatientBirthDate, AnonymizedPatientBirthDate ) values ( ?, ?, ?, ?, ?, ?, ?, ?, ? )",
                        new Object[] { pool.poolKey, opn, apn, opi, api, oan, aan, opbd, apbd });
              } else {
                template
                    .update(
                        "update ANONYMIZATIONMAP set UpdatedTimestamp = current_timestamp where PoolKey = ? and OriginalPatientName = ? and AnonymizedPatientName = ? and OriginalPatientID = ? and AnonymizedPatientID = ? and OriginalAccessionNumber = ? and AnonymizedAccessionNumber = ? and OriginalPatientBirthDate = ? and AnonymizedPatientBirthDate = ?",
                        new Object[] { pool.poolKey, opn, apn, opi, api, oan, aan, opbd, apbd });

              }
              template
                  .update(
                      "delete from ANONYMIZATIONMAPTEMP where PoolKey = ? and OriginalPatientName = ? and AnonymizedPatientName = ? and OriginalPatientID = ? and AnonymizedPatientID = ? and OriginalAccessionNumber = ? and AnonymizedAccessionNumber = ? and OriginalPatientBirthDate = ? and AnonymizedPatientBirthDate = ?",
                      new Object[] { pool.poolKey, opn, apn, opi, api, oan, aan, opbd, apbd });

            }
          });
        }
      }
    });
  }

  public void processAutoForward() {
    // First check our queue
    if (instancesToForward.isEmpty()) {
      return;
    }
    // Collect up all the files to forward, send them on and remove
    List<File> toProcess = new ArrayList<File>();

    while (!instancesToForward.isEmpty()) {
      toProcess.add(instancesToForward.poll());
    }
    // Get all the devices
    Session session = sessionFactory.openSession();

    Pool localPool = (Pool) session.byId(Pool.class).load(this.pool.poolKey);
    // Force load
    localPool.getDevices().size();

    for (Device device : localPool.getDevices()) {
      if (device.isAutoforward) {
        // Send our batch of files
        logger.debug("Autoforwarding " + toProcess.size() + " instance(s) to " + device);
        try {
          DcmSnd sender = new DcmSnd("autoforward to " + device);
          // Startup the sender
          sender.setRemoteHost(device.hostName);
          sender.setRemotePort(device.port);
          sender.setCalledAET(device.applicationEntityTitle);
          String calling = device.callingApplicationEntityTitle;
          if (calling == null) {
            calling = pool.applicationEntityTitle;
          }
          sender.setCalling(calling);
          for (File f : toProcess) {
            if (f.isFile()) {
              sender.addFile(f);
            }
          }
          sender.configureTransferCapability();
          sender.open();
          sender.send(null);
          sender.close();

        } catch (Exception e) {
          logger.error("Failed to autoforward to " + device, e);
        }
      }
    }
    session.close();
  }
}