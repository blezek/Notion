package edu.mayo.qia.pacs;

import com.codahale.metrics.CachedGauge;
import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.TimeUnit;

/**
 * This class is simply a catch-all for Notion metrics
 * 
 * @author Daniel Blezek
 *
 */
public class Metrics {

  static public void register() {
    // Counts by DBTable
    final JdbcTemplate template = Notion.context.getBean("template", JdbcTemplate.class);
    for (final String table : new String[] { "POOL", "INSTANCE", "SERIES", "STUDY" }) {
      Notion.metrics.register(MetricRegistry.name("DB", "table", table.toLowerCase()), new CachedGauge<Long>(30, TimeUnit.SECONDS) {
        @Override
        protected Long loadValue() {
          String sql = "select count(*) from " + table;
          return template.queryForObject(sql, Long.class);
        }
      });
    }
  }

  // Get a counter
  public static Counter getCounter(String name) {
    return Notion.metrics.getCounters().get(name);
  }

}
