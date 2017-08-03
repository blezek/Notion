package edu.mayo.qia.pacs.components;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import org.apache.log4j.Logger;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.net.CommandUtils;
import org.dcm4che2.net.DimseRSP;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import edu.mayo.qia.pacs.Notion;
import edu.mayo.qia.pacs.ctp.Anonymizer;
import edu.mayo.qia.pacs.dicom.DcmMoveException;
import edu.mayo.qia.pacs.dicom.DcmQR;

@Entity
@Table
@JsonIgnoreProperties(ignoreUnknown = true)
public class Query {
  static Logger logger = Logger.getLogger(Query.class);

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public int queryKey;

  public String status;
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "MMM dd, yyyy HH:mm")
  public Date createdTimestamp = new Date();
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "MMM dd, yyyy HH:mm")
  public Date lastQueryTimestamp = new Date();

  @ManyToOne(cascade = { CascadeType.PERSIST, CascadeType.MERGE }, fetch = FetchType.EAGER)
  @JoinColumn(name = "PoolKey")
  public Pool pool;

  @ManyToOne(cascade = { CascadeType.PERSIST, CascadeType.MERGE }, fetch = FetchType.EAGER)
  @JoinColumn(name = "DestinationPoolKey")
  public Pool destinationPool;

  @ManyToOne(cascade = { CascadeType.PERSIST, CascadeType.MERGE }, fetch = FetchType.EAGER)
  @JoinColumn(name = "QueryPoolKey")
  public Pool queryPool;

  @ManyToOne(cascade = { CascadeType.PERSIST, CascadeType.MERGE }, fetch = FetchType.EAGER)
  @JoinColumn(name = "DeviceKey")
  public Device device;

  @OneToMany(cascade = CascadeType.ALL, mappedBy = "query", fetch = FetchType.EAGER)
  public Set<Item> items = new HashSet<Item>();

  @JsonIgnore
  @Transient
  Future<String> queryFuture;
  @JsonIgnore
  @Transient
  Future<String> fetchFuture;

  @JsonIgnore
  static Timer queryTimer = Notion.metrics.timer(MetricRegistry.name("Query", "timer"));
  static Timer fetchTimer = Notion.metrics.timer(MetricRegistry.name("Fetch", "timer"));
  static Counter queryCounter = Notion.metrics.counter(MetricRegistry.name("Query", "counter"));
  static Counter fetchCounter = Notion.metrics.counter(MetricRegistry.name("Fetch", "counter"));
  static Counter pendingFetchCounter = Notion.metrics.counter(MetricRegistry.name("Fetch", "pending"));

  /**
   * Construct a query object and return it
   * 
   * @throws Exception
   */
  public static Query constructQuery(String filename, InputStream is) throws Exception {
    Query query = new Query();
    // Handle XLS for the moment
    Workbook workbook = null;
    if (filename.toLowerCase().endsWith(".xlsx")) {
      workbook = new XSSFWorkbook(is);
    }
    if (filename.toLowerCase().endsWith(".xls")) {
      workbook = new HSSFWorkbook(is);
    }

    Map<String, Integer> headerMap = new HashMap<String, Integer>();
    Notion.checkAssertion(workbook.getNumberOfSheets() > 0, "Could not find a sheet in the workbook");
    Sheet sheet = workbook.getSheetAt(0);
    Notion.checkAssertion(sheet.getPhysicalNumberOfRows() > 0, "Expecting a header row");
    Iterator<Cell> headerIterator = sheet.getRow(0).cellIterator();
    while (headerIterator.hasNext()) {
      Cell headerCell = headerIterator.next();
      headerMap.put(headerCell.getStringCellValue(), headerCell.getColumnIndex());
    }
    Notion.checkAssertion(headerMap.containsKey("PatientID"), "Could not find PatientID column");

    query.status = "Created";

    Iterator<Row> rowIterator = sheet.iterator();
    // Skip the header row
    rowIterator.next();
    while (rowIterator.hasNext()) {

      Row row = rowIterator.next();
      Item item = new Item();

      Notion.checkAssertion(row.getCell(headerMap.get("PatientID")) != null, "Row " + row.getRowNum() + " does not contain a PatientID");
      item.status = "created";
      item.patientName = getColumn(headerMap, row, "PatientName");
      item.patientID = getColumn(headerMap, row, "PatientID");
      item.accessionNumber = getColumn(headerMap, row, "AccessionNumber");
      item.patientBirthDate = getColumn(headerMap, row, "PatientBirthDate");
      item.studyDate = getColumn(headerMap, row, "StudyDate");
      item.modalitiesInStudy = getColumn(headerMap, row, "ModalitiesInStudy");
      item.studyDescription = getColumn(headerMap, row, "StudyDescription");
      item.anonymizedID = getColumn(headerMap, row, "AnonymizedID");
      item.anonymizedName = getColumn(headerMap, row, "AnonymizedName");
      if (item.patientID != null) {
        item.query = query;
        query.items.add(item);
      }
    }
    return query;
  }

  static String getAttribute(JsonNode node, String key) {
    return node.has(key) ? node.get(key).asText() : null;
  }

  public static Query constructQuery(JsonNode json) {
    Query query = new Query();
    Iterator<JsonNode> iterator = json.get("items").elements();
    while (iterator.hasNext()) {
      JsonNode node = iterator.next();
      Item item = new Item();
      item.patientID = getAttribute(node, "PatientID");
      item.patientName = getAttribute(node, "PatientName");
      item.anonymizedName = getAttribute(node, "AnonymizedName");
      item.anonymizedID = getAttribute(node, "AnonymizedID");
      if (item.patientID != null) {
        query.items.add(item);
        item.query = query;
      }
    }
    return query;
  }

  static String getColumn(Map<String, Integer> headerMap, Row row, String column) {
    if (!headerMap.containsKey(column)) {
      return null;
    }
    Cell cell = row.getCell(headerMap.get(column));
    if (cell == null) {
      return null;
    }
    if (cell.getCellType() == Cell.CELL_TYPE_NUMERIC) {
      return Double.toString(cell.getNumericCellValue());
    }
    if (cell.getCellType() == Cell.CELL_TYPE_STRING) {
      return cell.getStringCellValue();
    } else {
      return null;
    }
  }

  // Implement a C-FIND and store results away...
  public void executeQuery() {
    if (queryFuture != null && !queryFuture.isDone()) {
      return;
    }
    ExecutorService executor = Notion.context.getBean("executor", ExecutorService.class);
    queryFuture = executor.submit(new Callable<String>() {
      @Override
      public String call() {
        Thread.currentThread().setName("Query " + device);
        queryCounter.inc();
        Timer.Context context = queryTimer.time();
        JdbcTemplate template = Notion.context.getBean(JdbcTemplate.class);
        template.update("update QUERY set Status = ? where QueryKey = ?", "query pending", queryKey);
        template.update("update QUERY set LastQueryTimestamp = ? where QueryKey = ?", new Date(), queryKey);
        template.update("update QUERYITEM set Status = ? where QueryKey = ?", "query pending", queryKey);
        for (Item item : items) {
          template.update("delete from QUERYRESULT where QueryItemKey = ?", item.queryItemKey);
        }
        for (final Item item : items) {
          template.update("update QUERYITEM set Status = ? where QueryItemKey = ?", "working", item.queryItemKey);

          DcmQR dcmQR = new DcmQR(queryPool.applicationEntityTitle);
          dcmQR.setRemoteHost(device.hostName);
          dcmQR.setRemotePort(device.port);
          dcmQR.setCalledAET(device.applicationEntityTitle);
          dcmQR.setCalling(queryPool.applicationEntityTitle);

          // Add our query parameters
          Map<String, String> map = item.getTagMap();
          for (String key : map.keySet()) {
            dcmQR.addMatchingKey(Tag.toTagPath(key), map.get(key));
          }
          try {
            dcmQR.open();
            DimseRSP response = dcmQR.queryAll();
            while (response.next()) {
              DicomObject command = response.getCommand();
              if (CommandUtils.isPending(command)) {
                DicomObject ds = response.getDataset();
                String status = ds.contains(Tag.StudyInstanceUID) ? "success" : "fail";
                //@formatter:off
                template.update("insert into QUERYRESULT ( "
                    + "QueryItemKey,"
                    + "Status,"
                    + "DoFetch,"
                    + "StudyInstanceUID,"
                    + "PatientName,"
                    + "PatientID,"
                    + "AccessionNumber,"
                    + "PatientBirthDate,"
                    + "StudyDate,"
                    + "ModalitiesInStudy,"
                    + "StudyDescription"
                    + " ) VALUES ( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? )",
                    item.queryItemKey,
                    status,
                    "F",
                    ds.getString(Tag.StudyInstanceUID ),
                    ds.getString(Tag.PatientName, (String)null),
                    ds.getString(Tag.PatientID, (String)null),
                    ds.getString(Tag.AccessionNumber, (String)null),
                    ds.getString(Tag.PatientBirthDate, (String)null),
                    ds.getString(Tag.StudyDate, (String)null),
                    ds.getString(Tag.ModalitiesInStudy, (String)null),
                    ds.getString(Tag.StudyDescription, (String)null)
                    );
                //@formatter:on
              }
            }
            template.update("update QUERYITEM set Status = ? where QueryItemKey = ?", "query complete", item.queryItemKey);
          } catch (Exception e) {
            logger.error("Caught error in query", e);
            template.update("update QUERYITEM set Status = ? where QueryItemKey = ?", "query failed", item.queryItemKey);
          } finally {
            try {
              dcmQR.close();
            } catch (Exception e) {
              logger.error("Error closing query connection", e);
            }
          }
        }
        template.update("update QUERY set Status = ? where QueryKey = ?", "query completed", queryKey);
        Thread.currentThread().setName("Idle");
        queryCounter.dec();
        context.stop();
        return "completed";
      }
    });
  }

  /** Update the query, by triggering a fetch on each query result */
  public void update(Query update) {
    if (update.queryKey == this.queryKey) {
      JdbcTemplate template = Notion.context.getBean(JdbcTemplate.class);
      for (Item updateItem : update.items) {
        // Find this item...
        for (Item item : items) {
          if (item.queryItemKey == updateItem.queryItemKey) {
            for (Result updateResult : updateItem.items) {
              for (Result result : item.items) {
                if (result.queryResultKey == updateResult.queryResultKey) {
                  logger.debug(updateResult.doFetch);
                  String doFetch = updateResult.doFetch ? "T" : "F";
                  template.update("update QUERYRESULT set DoFetch = ? where QueryResultKey = ?", doFetch, result.queryResultKey);
                  continue;
                }
              }
            }
            continue;
          }
        }
      }
    }
  }

  public void doFetch() {
    logger.debug("Queuing fetch");

    if (fetchFuture != null && !fetchFuture.isDone()) {
      return;
    }

    final JdbcTemplate template = Notion.context.getBean(JdbcTemplate.class);
    template.update("update QUERY set Status = ? where QueryKey = ?", "fetch pending", queryKey);

    fetchFuture = Notion.context.getBean("executor", ExecutorService.class).submit(new Callable<String>() {
      @Override
      public String call() {
        final JdbcTemplate template = Notion.context.getBean(JdbcTemplate.class);
        Timer.Context context = fetchTimer.time();
        fetchCounter.inc();

        template.update("update QUERY set Status = ? where QueryKey = ?", "fetch pending", queryKey);
        template.update("update QUERYRESULT set Status = ? where QueryItemKey in ( select QueryItemKey from QUERYITEM where QueryKey = ?) ", "fetch pending", queryKey);
        template.update("update QUERYRESULT set Status = ? where QueryItemKey in ( select QueryItemKey from QUERYITEM where QueryKey = ?) and DoFetch = 'F'", "", queryKey);
        Thread.currentThread().setName("Fetch " + device);
        PoolManager poolManager = Notion.context.getBean(PoolManager.class);
        PoolContainer poolContainer = poolManager.getContainer(pool.poolKey);
        Anonymizer anonymizer = Notion.context.getBean("anonymizer", Anonymizer.class);
        anonymizer.setPool(poolContainer.getPool());

        for (final Item item : items) {
          for (final Result result : item.items) {
            if (result.doFetch) {
              pendingFetchCounter.inc();
            }
          }
        }

        for (final Item item : items) {
          for (final Result result : item.items) {
            if (!result.doFetch) {
              template.update("update QUERYRESULT set Status = ? where QueryResultKey = ?", "", result.queryResultKey);
              continue;
            }

            template.update("update QUERYRESULT set Status = ? where QueryResultKey = ?", "fetching", result.queryResultKey);
            DcmQR dcmQR = new DcmQR(queryPool.applicationEntityTitle);
            dcmQR.setRemoteHost(device.hostName);
            dcmQR.setRemotePort(device.port);
            dcmQR.setCalledAET(device.applicationEntityTitle);
            dcmQR.setCalling(queryPool.applicationEntityTitle);
            dcmQR.setMoveDest(destinationPool.applicationEntityTitle);
            try {
              if (destinationPool.anonymize) {
                throw new Exception("Destination pool " + destinationPool.name + "/" + destinationPool.applicationEntityTitle + " is anonymizing, this move will fail.  Please turn off anonymization on the destination pool");
              }
              // Create the entries
              if (item.anonymizedName != null && item.anonymizedName.length() > 0) {
                anonymizer.setValue("PatientName", result.patientName, item.anonymizedName);
              }
              if (item.anonymizedID != null && item.anonymizedID.length() > 0) {
                anonymizer.setValue("PatientID", result.patientID, item.anonymizedID);
              }
              dcmQR.qrStudy(result.studyInstanceUID);
              template.update("update QUERYRESULT set Status = ? where QueryResultKey = ?", "moving", result.queryResultKey);

              // Initiate the move
              MoveStatus status = new MoveStatus();
              if (pool.poolKey != destinationPool.poolKey) {
                // Move and delete, otherwise ignore
                if (!poolManager.getContainer(destinationPool.poolKey).moveStudyTo(result.studyInstanceUID, poolContainer, status)) {
                  template.update("update QUERYRESULT set Status = ? where QueryResultKey = ?", "fail: could not move study ", result.queryResultKey);
                } else {
                  // Delete the study
                  poolManager.getContainer(destinationPool.poolKey).deleteStudy(result.studyInstanceUID);
                }
              } else {
                // Fetched to ourselves, so update the StudyKey accordingly
                Integer studyKey = template.queryForObject("select StudyKey from STUDY where PoolKey = ? and StudyInstanceUID = ?", new Object[] { pool.poolKey, result.studyInstanceUID }, Integer.class);
                status.movedStudyKey = studyKey;
              }
              // Update the result's StudyKey
              template.update("update QUERYRESULT set StudyKey = ?, Status = ? where QueryResultKey = ?", status.movedStudyKey, "completed", result.queryResultKey);

            } catch (DcmMoveException e) {
              template.update("update QUERYRESULT set Status = ? where QueryResultKey = ?", "fail: " + e.toString(), result.queryResultKey);
            } catch (Exception e) {
              template.update("update QUERYRESULT set Status = ? where QueryResultKey = ?", "fail: unknown exception " + e.toString(), result.queryResultKey);
              logger.error("Error doing query", e);
            }
            pendingFetchCounter.dec();
          }
        }
        template.update("update QUERY set Status = ? where QueryKey = ?", "fetch completed", queryKey);
        logger.debug("Fetch Compeleted");
        fetchCounter.dec();
        Thread.currentThread().setName("Idle");
        context.stop();
        return "complete";
      }
    });
  }

  /** Form the CVS for this query. */
  @Transient
  @JsonIgnore
  public Workbook generateSpreadSheet() {
    final JdbcTemplate template = Notion.context.getBean(JdbcTemplate.class);

    XSSFWorkbook workbook = new XSSFWorkbook();
    final XSSFSheet sheet = workbook.createSheet("query");
    XSSFRow header = sheet.createRow(0);
    header.createCell(0, Cell.CELL_TYPE_STRING).setCellValue("PatientName");
    header.createCell(1, Cell.CELL_TYPE_STRING).setCellValue("PatientID");
    header.createCell(2, Cell.CELL_TYPE_STRING).setCellValue("AccessionNumber");
    header.createCell(3, Cell.CELL_TYPE_STRING).setCellValue("PatientBirthDate");
    header.createCell(4, Cell.CELL_TYPE_STRING).setCellValue("StudyDate");
    header.createCell(5, Cell.CELL_TYPE_STRING).setCellValue("ModalitiesInStudy");
    header.createCell(6, Cell.CELL_TYPE_STRING).setCellValue("StudyDescription");
    header.createCell(7, Cell.CELL_TYPE_STRING).setCellValue("AnonymizedID");
    header.createCell(8, Cell.CELL_TYPE_STRING).setCellValue("AnonymizedName");

    DataFormat fmt = workbook.createDataFormat();
    CellStyle textStyle = workbook.createCellStyle();
    textStyle.setDataFormat(fmt.getFormat("@"));

    for (int i = 0; i < 9; i++) {
      sheet.setDefaultColumnStyle(i, textStyle);
    }

    template.query("select * from QUERYITEM where QueryKey = ?", new Object[] { queryKey }, new RowCallbackHandler() {
      int rowNumber = 1;

      @Override
      public void processRow(ResultSet rs) throws SQLException {
        XSSFRow row = sheet.createRow(rowNumber);
        rowNumber++;
        XSSFCell cell;
        cell = row.createCell(0, Cell.CELL_TYPE_STRING);
        cell.setCellType(Cell.CELL_TYPE_STRING);
        cell.setCellValue(rs.getString("PatientName"));
        cell.setCellType(Cell.CELL_TYPE_STRING);
        cell = row.createCell(1, Cell.CELL_TYPE_STRING);
        cell.setCellType(Cell.CELL_TYPE_STRING);
        cell.setCellValue(rs.getString("PatientID"));
        cell.setCellType(Cell.CELL_TYPE_STRING);
        cell = row.createCell(2, Cell.CELL_TYPE_STRING);
        cell.setCellType(Cell.CELL_TYPE_STRING);
        cell.setCellValue(rs.getString("AccessionNumber"));
        cell.setCellType(Cell.CELL_TYPE_STRING);
        cell = row.createCell(3, Cell.CELL_TYPE_STRING);
        cell.setCellType(Cell.CELL_TYPE_STRING);
        cell.setCellValue(rs.getString("PatientBirthDate"));
        cell.setCellType(Cell.CELL_TYPE_STRING);
        cell = row.createCell(4, Cell.CELL_TYPE_STRING);
        cell.setCellType(Cell.CELL_TYPE_STRING);
        cell.setCellValue(rs.getString("StudyDate"));
        cell.setCellType(Cell.CELL_TYPE_STRING);
        cell = row.createCell(5, Cell.CELL_TYPE_STRING);
        cell.setCellType(Cell.CELL_TYPE_STRING);
        cell.setCellValue(rs.getString("ModalitiesInStudy"));
        cell.setCellType(Cell.CELL_TYPE_STRING);
        cell = row.createCell(6, Cell.CELL_TYPE_STRING);
        cell.setCellType(Cell.CELL_TYPE_STRING);
        cell.setCellValue(rs.getString("StudyDescription"));
        cell.setCellType(Cell.CELL_TYPE_STRING);
        cell = row.createCell(7, Cell.CELL_TYPE_STRING);
        cell.setCellType(Cell.CELL_TYPE_STRING);
        cell.setCellValue(rs.getString("AnonymizedID"));
        cell.setCellType(Cell.CELL_TYPE_STRING);
        cell = row.createCell(8, Cell.CELL_TYPE_STRING);
        cell.setCellType(Cell.CELL_TYPE_STRING);
        cell.setCellValue(rs.getString("AnonymizedName"));
        cell.setCellType(Cell.CELL_TYPE_STRING);

      }
    });
    return workbook;

  }
}
