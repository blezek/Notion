package edu.mayo.qia.pacs.rest;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.jersey.spi.resource.PerRequest;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;

import edu.mayo.qia.pacs.Notion;
import edu.mayo.qia.pacs.components.PoolManager;
import edu.mayo.qia.pacs.ctp.Anonymizer;

@Scope("prototype")
@Component
@PerRequest
public class LookupEndpoint extends TableEndpoint {
  static Logger logger = Logger.getLogger(LookupEndpoint.class);

  @Autowired
  JdbcTemplate template;

  @Autowired
  PoolManager poolManager;

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  public Response getCSV() throws Exception {
    // Return the lookup values as CSV

    StreamingOutput stream = new StreamingOutput() {
      @Override
      public void write(OutputStream output) throws IOException {
        final PrintWriter writer = new PrintWriter(output);
        writer.println("OriginalPatientName, AnonymizedPatientName, OriginalPatientID, AnonymizedPatientID, OriginalAccessionNumber, AnonymizedAccessionNumber, OriginalPatientBirthDate, AnonymizedPatientBirthDate");
        template
            .query(
                "select distinct OriginalPatientName, AnonymizedPatientName, OriginalPatientID, AnonymizedPatientID, OriginalAccessionNumber, AnonymizedAccessionNumber, OriginalPatientBirthDate, AnonymizedPatientBirthDate from ANONYMIZATIONMAP where PoolKey = ?",
                new Object[] { poolKey }, new RowCallbackHandler() {

                  @Override
                  public void processRow(ResultSet rs) throws SQLException {
                    String delim = "";
                    for (int i = 1; i <= 8; i++) {
                      writer.print(delim + rs.getString(i));
                      delim = ",";
                    }
                    writer.println();
                  }
                });
        writer.close();
      }
    };

    String fn = poolManager.getContainer(poolKey).getPool().applicationEntityTitle + "-Lookup-" + new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(new Date()) + ".csv";
    return Response.ok(stream).header("content-disposition", "attachment; filename = " + fn).build();
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  public Response getLookupData(@Context UriInfo uriInfo) throws Exception {
    ObjectNode json = super.get(uriInfo.getQueryParameters(), "LOOKUP", " and Visible = true ", new String[] { "Type", "Name", "Value" }, "LookupKey");
    return Response.ok(json).build();
  }

  @POST
  @Path("/create")
  @Produces(MediaType.APPLICATION_JSON)
  public Response createRecord(@Context UriInfo uriInfo, @FormParam("Type") String type, @FormParam("Name") String name, @FormParam("Value") String value) throws Exception {
    logger.debug("Create: " + type + "/" + name + "/" + value);
    Anonymizer anonymizer = Notion.context.getBean("anonymizer", Anonymizer.class);
    anonymizer.setPool(poolManager.getContainer(poolKey).getPool());
    ObjectNode json;
    // Does it already exist?
    Object[] k = anonymizer.lookupValueAndKey(type, name);
    if (k[0] != null) {
      // Already exists
      json = new ObjectMapper().createObjectNode();
      json.put("Result", "ERROR");
      json.put("Message", "A lookup value for " + type + "/" + name + " already exists");
      return Response.ok(json).build();
    }

    int lookupKey = anonymizer.setValue(type, name, value);
    json = super.get(uriInfo.getQueryParameters(), "LOOKUP", " and Visible = true and LookupKey = " + lookupKey, new String[] { "Type", "Name", "Value" }, "LookupKey");
    // Change Records into Record!
    json.put("Record", json.withArray("Records").get(0));
    json.remove("TotalRecordCount");
    json.remove("Records");
    return Response.ok(json).build();
  }

  @POST
  @Path("/update")
  @Produces(MediaType.APPLICATION_JSON)
  public Response createRecord(@Context UriInfo uriInfo, @FormParam("LookupKey") final Integer lookupKey, @FormParam("Type") final String type, @FormParam("Name") final String name, @FormParam("Value") final String value) throws Exception {
    logger.debug("Update: " + lookupKey + "/" + type + "/" + name + "/" + value);
    Anonymizer anonymizer = Notion.context.getBean("anonymizer", Anonymizer.class);
    anonymizer.setPool(poolManager.getContainer(poolKey).getPool());
    SimpleResponse json = new SimpleResponse();
    // Does it already exist?
    int count = template.queryForObject("select count(*) from LOOKUP where PoolKey = ? and LookupKey = ?", new Object[] { poolKey, lookupKey }, Integer.class);
    if (count != 1) {
      // Already exists
      json.put("Result", "ERROR");
      json.put("Message", "A lookup value does not exist");
      return Response.ok(json).build();
    }
    count = template.update("update LOOKUP set Name = ?, Value = ? where PoolKey = ? and LookupKey = ?", name, value, poolKey, lookupKey);
    if (count == 1) {
      json.put("Result", "OK");
    } else {
      // Already exists
      json.put("Result", "ERROR");
      json.put("Message", "Updated failed to update exactly one row");
      return Response.ok(json).build();
    }
    json.put("Result", "OK");
    return Response.ok(json).build();
  }

  @POST
  @Path("/delete")
  @Produces(MediaType.APPLICATION_JSON)
  public Response deleteRecord(@FormParam("LookupKey") String lookupKey) throws Exception {
    SimpleResponse json = new SimpleResponse();

    int rows = template.update("delete from LOOKUP where PoolKey = ? and LookupKey = ?", new Object[] { poolKey, lookupKey });
    if (rows == 1) {
      json.put("Result", "OK");
    } else {
      json.put("Result", "ERROR");
      json.put("Message", "Delete failed, deleted " + rows + " rows");
    }
    return Response.ok(json).build();
  }
}
