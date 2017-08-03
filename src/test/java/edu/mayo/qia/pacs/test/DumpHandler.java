package edu.mayo.qia.pacs.test;

import org.apache.log4j.Logger;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowCountCallbackHandler;

import java.sql.ResultSet;
import java.sql.SQLException;

public class DumpHandler extends RowCountCallbackHandler {

  static public RowCallbackHandler getDumper(final Logger logger) {
    return new RowCallbackHandler() {

      @Override
      public void processRow(ResultSet rs) throws SQLException {
        if (rs == null) {
          return;
        }
        if (logger.isDebugEnabled()) {
          StringBuilder buffer = new StringBuilder();
          for (int i = 1; i <= rs.getMetaData().getColumnCount(); ++i) {
            Object o = rs.getObject(i);
            buffer.append(rs.getMetaData().getColumnName(i)).append(": ").append(o == null ? "<null>" : o.toString()).append(" ");
          }
          logger.debug(buffer);
        }
      }
    };
  }
}
