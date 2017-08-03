package edu.mayo.qia.pacs.ctp;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.dcm4che.dict.Tags;
import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.SpecificCharacterSet;
import org.dcm4che2.data.Tag;
import org.dcm4che2.io.DicomInputStream;
import org.dcm4che2.io.DicomOutputStream;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.ScriptableObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.util.DigestUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import edu.mayo.qia.pacs.Notion;
import edu.mayo.qia.pacs.components.Pool;
import edu.mayo.qia.pacs.components.PoolContainer;
import edu.mayo.qia.pacs.components.Script;

@Component
@Scope("prototype")
/** Helper class containing some functions to use for the Anonymizer */
public class Anonymizer {
  static Logger logger = Logger.getLogger(Anonymizer.class);
  protected Pool pool;

  @Autowired
  JdbcTemplate template;

  public static Map<Integer, String> fieldMap = new HashMap<Integer, String>();
  static {
    Field[] fields = Tags.class.getDeclaredFields();
    for (Field field : fields) {
      if (field.getType() == int.class) {
        try {
          fieldMap.put(field.getInt(Tag.class), field.getName());
        } catch (Exception e) {
          logger.error("Error extracting field: " + field, e);
        }
      }
    }
  }

  public void setPool(Pool pool) {
    this.pool = pool;
  }

  public ScriptableObject setBindings(ScriptableObject scope, DicomObject tags) {
    ScriptableObject.putProperty(scope, "pool", pool);
    ScriptableObject.putProperty(scope, "anon", this);
    ScriptableObject.putProperty(scope, "anonymizer", this);
    NativeObject tagObject = new NativeObject();

    SpecificCharacterSet cs = tags.getSpecificCharacterSet();
    Iterator<DicomElement> iterator = tags.datasetIterator();
    while (iterator.hasNext()) {
      DicomElement element = iterator.next();

      // String tagName =
      // ElementDictionary.getDictionary().nameOf(element.tag());
      // tagName = tagName.replaceAll("[ ']+", "");
      String tagName = fieldMap.get(element.tag());
      try {
        if (!element.hasItems()) {
          tagObject.defineProperty(tagName, element.getString(cs, false), ScriptableObject.READONLY);
        }
      } catch (UnsupportedOperationException e) {
        logger.warn("Could not process tag: " + tagName + " unable to convert to a string: " + e.getMessage());
      }
      // logger.info("Setting: " + tagName + ": " +
      // tags.getString(element.tag()));
    }
    ScriptableObject.putProperty(scope, "tags", tagObject);
    return scope;
  }

  public void debug(String msg) {
    logger.debug("Script: " + msg);
  }

  public void info(String msg) {
    logger.info("Script: " + msg);
  }

  public void warn(String msg) {
    logger.warn("Script: " + msg);
  }

  public void error(String msg) {
    logger.error("Script: " + msg);
  }

  public void exception(String msg) throws Exception {
    throw new Exception(msg);
  }

  public String hash(String value) {
    return hash(value, value.length());
  }

  public String lookup(String type, String name) {
    Object[] v = lookupValueAndKey(type, name);
    return (String) v[0];
  }

  public Object[] lookupValueAndKey(final String type, final String name) {
    final Object[] out = new Object[] { null, null };
    template.query("select Value, LookupKey from LOOKUP where PoolKey = ? and Type = ? and Name = ?", new Object[] { pool.poolKey, type, name }, new RowCallbackHandler() {

      @Override
      public void processRow(ResultSet rs) throws SQLException {
        out[0] = rs.getString("Value");
        out[1] = rs.getInt("LookupKey");
      }
    });

    return out;
  }

  public Integer setValue(final String type, final String name, final String value) {
    return setValue(type, name, value, true);
  }

  Integer setValue(final String type, final String name, final String value, final Boolean visible) {
    Object[] k = lookupValueAndKey(type, name);
    if (k[1] != null) {
      // Update it
      template.update("update LOOKUP set Value = ? where LookupKey = ?", value, k[1]);
      return (Integer) k[1];
    } else {
      KeyHolder keyHolder = new GeneratedKeyHolder();
      template.update(new PreparedStatementCreator() {

        @Override
        public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
          PreparedStatement statement = con.prepareStatement("insert into LOOKUP ( PoolKey, Type, Name, Value, Visible ) VALUES ( ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
          statement.setInt(1, pool.poolKey);
          statement.setString(2, type);
          statement.setString(3, name);
          statement.setString(4, value);
          statement.setBoolean(5, visible);
          return statement;
        }
      }, keyHolder);
      return keyHolder.getKey().intValue();
    }

  }

  String getSequence(final String type) {
    final String internalType = "Sequence." + type;
    Object[] k = lookupValueAndKey("Pool", internalType);
    String sequenceName = null;
    if (k[0] == null) {
      // Create an empty, grab a sequence from the POOL UID
      Integer i = template.queryForObject("VALUES( NEXT VALUE FOR UID" + pool.poolKey + ")", Integer.class);
      sequenceName = "pool_sequence_" + pool.poolKey + "_" + i;
      template.update("create sequence " + sequenceName + " AS INT START WITH 1");
      setValue("Pool", internalType, sequenceName, false);
    } else {
      sequenceName = (String) k[0];
    }
    return sequenceName;
  }

  public int sequenceNumber(final String type, final String name) {
    String internalType = "Sequence." + type;
    String v = lookup(internalType, name);
    if (v != null) {
      return Integer.decode(v);
    } else {
      String sequence = getSequence(internalType);
      Integer i = template.queryForObject("VALUES( NEXT VALUE FOR " + sequence + ")", Integer.class);
      setValue(internalType, name, i.toString(), false);
      return i;
    }
  }

  public String hash(String value, int length) {
    String result = value;
    try {
      result = DigestUtil.getUSMD5(value);
    } catch (Exception e) {
      logger.error("Failed to compute MD5 hash", e);
    }
    return result.substring(0, length);
  }

  public static Map<String, String> unwrapResult(NativeObject no) {
    Map<String, String> map = new HashMap<String, String>();

    for (Object id : ScriptableObject.getPropertyIds(no)) {
      String key = id.toString();

      Object o = ScriptableObject.getProperty(no, key);
      String value = o.toString();
      if (o instanceof NativeJavaObject) {
        value = ((NativeJavaObject) o).unwrap().toString();
      }
      map.put(key, value);
    }
    return map;
  }

  public static String tryScript(PoolContainer poolContainer, Script script) {
    Anonymizer function = new MockAnonymizer();
    function.setPool(poolContainer.getPool());
    final Context context = Context.enter();
    final ScriptableObject scope = function.setBindings(context.initStandardObjects(), null);
    Object result = null;
    try {
      try {
        result = context.evaluateString(scope, script.script, "inline", 1, null);
      } catch (Exception e) {
        logger.error("Failed to process the script correctly: " + script, e);
        return "Failed to process the script correctly: " + e.getMessage();
      }
      if (result instanceof NativeObject) {
        return unwrapResult(((NativeObject) result)).toString();
      } else {
        return "Error: result must be a Javascript hash or object";
      }

    } finally {
      Context.exit();
    }
  }

  public static FileObject process(PoolContainer poolContainer, FileObject fileObject, DicomObject originalTags) throws Exception {
    JdbcTemplate template = Notion.context.getBean("template", JdbcTemplate.class);

    Anonymizer function = Notion.context.getBean("anonymizer", Anonymizer.class);
    function.setPool(poolContainer.getPool());
    DicomInputStream dis = new DicomInputStream(fileObject.getFile());
    final DicomObject dcm = dis.readDicomObject();
    dis.close();

    final Context context = Context.enter();
    // Run through all the stored bindings
    String script = template.queryForObject("select Script from SCRIPT where PoolKey = ?", new Object[] { poolContainer.getPool().poolKey }, String.class);
    final ScriptableObject scope = function.setBindings(context.initStandardObjects(), originalTags);
    try {
      Object result = null;
      try {
        result = context.evaluateString(scope, script, "inline", 1, null);
      } catch (Exception e) {
        logger.error("Failed to process the script correctly: " + script, e);
        throw e;
      }
      if (result instanceof NativeObject) {

        NativeObject no = (NativeObject) result;
        Map<String, String> map = unwrapResult(no);
        for (String tagName : map.keySet()) {
          String value = map.get(tagName);
          try {
            dcm.putString(Tag.toTag(tagName), null, value);
          } catch (Exception e) {
            logger.error("Could not put the value (" + result + ") back into the DICOM image for tag: " + tagName, e);
            throw new Exception("Could not put the value (" + result + ") back into the DICOM image for tag: " + tagName + " error: " + e.getMessage());
          }
        }

      } else {
        logger.error("Expected a map back from script, but instead got: " + result.toString());
        throw new Exception("Expected a string back from script, but instead got: " + result.toString());
      }

    } finally {
      Context.exit();
    }

    File output = new File(fileObject.getFile().getParentFile(), UUID.randomUUID().toString());
    FileOutputStream fos = new FileOutputStream(output);
    BufferedOutputStream bos = new BufferedOutputStream(fos);
    DicomOutputStream dos = new DicomOutputStream(bos);
    dos.writeDicomFile(dcm);
    dos.close();
    fos.close();

    fileObject.getFile().delete();
    FileUtils.moveFile(output, fileObject.getFile());

    return fileObject;
  }
}
