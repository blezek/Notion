package edu.mayo.qia.pacs.job;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;

import edu.mayo.qia.pacs.Notion;

public class HashCleaner implements Job {
  static Logger logger = Logger.getLogger(HashCleaner.class);

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    JdbcTemplate template = Notion.context.getBean(JdbcTemplate.class);

    Timestamp now = new Timestamp(new DateTime().getMillis());
    try {
      template.update("delete from VIEWERHASH where Expires < ?", new Object[] { now });
    } catch (Exception e) {
      logger.error("error cleaning expired hashes", e);
    }

  }
}