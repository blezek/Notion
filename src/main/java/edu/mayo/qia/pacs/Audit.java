package edu.mayo.qia.pacs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.shiro.subject.Subject;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.mayo.qia.pacs.ctp.Anonymizer;

/**
 * Contains a logger and methods for constructing an audit trail.
 * 
 * Logs should conform
 * <code>Audit.logger.info("user=" + subject.getPrincipal().toString() + " action=view_study value=" + row.toString());</code>
 * to aid in parsing later.
 * 
 * @author Daniel Blezek
 *
 */
public class Audit {
  static Logger logger = LoggerFactory.getLogger("edu.mayo.qia.notion.audit");

  public static int[] tagFields = { Tag.PatientID, Tag.PatientName, Tag.StudyDate, Tag.Modality, Tag.StudyID, Tag.SeriesNumber, Tag.AccessionNumber, Tag.StudyDescription, Tag.SeriesDescription };

  public static void log(Subject subject, String action, String value) {
    log(subject.getPrincipal().toString(), action, value);
  }

  public static void log(String user, String action, String value) {

    ObjectNode node = Notion.context.getBean("objectMapper", ObjectMapper.class).createObjectNode();
    node.put("value", value);
    log(user, action, node);
  }

  public static void log(String user, String action, ObjectNode value) {
    ObjectNode node = Notion.context.getBean("objectMapper", ObjectMapper.class).createObjectNode();
    node.put("user", user);
    node.put("action", action);
    node.set("value", value);
    DateTimeFormatter f = ISODateTimeFormat.dateHourMinuteSecondMillis();
    node.put("timestamp", f.print(new DateTime()));
    logger.info(node.toString());
  }

  public static void log(Subject subject, String action, ObjectNode json) {
    log(subject.getPrincipal().toString(), action, json);
  }

  public static void log(String string, String action, DicomObject tags) {
    ObjectNode node = Notion.context.getBean("objectMapper", ObjectMapper.class).createObjectNode();
    for (int t : tagFields) {
      try {
        node.put(Anonymizer.fieldMap.get(t), tags.getString(t));
      } catch (Exception e) {
      }
    }
    log(string, action, node);
  }

}
