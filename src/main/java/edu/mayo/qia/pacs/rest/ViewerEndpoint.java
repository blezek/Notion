package edu.mayo.qia.pacs.rest;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.jersey.api.client.ClientResponse.Status;

import org.apache.log4j.Logger;
import org.apache.shiro.subject.Subject;
import org.secnod.shiro.jaxrs.Auth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;

import edu.mayo.qia.pacs.components.PoolContainer;
import edu.mayo.qia.pacs.components.PoolManager;

@Component
@Scope("singleton")
public class ViewerEndpoint extends Endpoint {
  static Logger logger = Logger.getLogger(ViewerEndpoint.class);
  public int poolKey;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  JdbcTemplate template;

  @Autowired
  PoolManager poolManager;

  /**
   * Study list in JSON format
   * 
   * Return a list of studies following the Cornerstone example JSON file <code>
   {
    "studyList": [
        {
            "patientName" : "MISTER^MR",
            "patientId" : "832040",
            "studyDate" : "20010108",
            "modality" : "MR",
            "studyDescription" :"BRAIN SELLA",
            "numImages" : 17,
            "studyId" : "mrstudy"
        },
   </code>
   * 
   * @param subject
   *        Authorized user account
   * @return
   */
  @GET
  @Path("studies")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getStudies(@Auth Subject subject) {
    ObjectNode json = objectMapper.createObjectNode();
    final ArrayNode studies = json.putArray("studyList");
    template.query("select * from STUDY where PoolKey = ? order by PatientName", new Object[] { poolKey }, new RowCallbackHandler() {

      @Override
      public void processRow(ResultSet rs) throws SQLException {
        ObjectNode study = studies.addObject();
        study.put("patientName", rs.getString("PatientName"));
        study.put("patientId", rs.getString("PatientID"));
        study.put("studyDate", rs.getString("StudyDate"));
        study.put("modality", "unknown");
        study.put("studyDescription", rs.getString("StudyDescription"));
        study.put("studyId", rs.getString("StudyKey"));
      }
    });
    return Response.ok(json).build();
  }

  /**
   * Series list in JSON format
   * 
   * Return series data in the format Cornerson is expecting. <code>
   {
    "patientName" : "MISTER^CT",
    "patientId" : "2178309",
    "studyDate" : "20010105",
    "modality" : "CT",
    "studyDescription" :"CHEST",
    "numImages" : 111,
    "studyId" : "ctstudy",
    "seriesList" : [
        {
            "seriesDescription": "Pelvis PA",
            "seriesNumber" : "1",
            "instanceList" : [
                {"imageId" : "CRStudy/1.3.51.5145.5142.20010109.1105627.1.0.1.dcm"}
            ]
        },
        {
            "seriesDescription": "PELVIS LAT",
            "seriesNumber" : "1",
            "instanceList" : [
                { "imageId" : "CRStudy/1.3.51.5145.5142.20010109.1105752.1.0.1.dcm" }
            ]
        }
     ]
   }
   </code>
   */
  @GET
  @Path("study/{id: [1-9][0-9]*}/series")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getSeries(@Auth Subject subject, @PathParam("id") int studyKey) {
    final ObjectNode json = objectMapper.createObjectNode();
    final ArrayNode series = json.putArray("seriesList");
    template.query("select * from STUDY where PoolKey = ? and StudyKey = ?", new Object[] { poolKey, studyKey }, new RowCallbackHandler() {

      @Override
      public void processRow(ResultSet rs) throws SQLException {
        json.put("patientName", rs.getString("PatientName"));
        json.put("patientId", rs.getString("PatientID"));
        json.put("studyDate", rs.getString("StudyDate"));
        json.put("modality", "unknown");
        json.put("studyDescription", rs.getString("StudyDescription"));
        json.put("studyId", rs.getString("StudyKey"));
        // int studyKey = rs.getInt("StudyKey");
        // Integer numberOfImages =
        // template.queryForObject("select count(INSTANCE.InstanceKey) from
        // INSTANCE, SERIES where INSTANCE.SeriesKey = SERIES.SeriesKey and
        // SERIES.StudyKey = ?",
        // new Object[] { studyKey }, Integer.class);
        // json.put("numImages", numberOfImages);

      }
    });

    template.query("select SERIES.* from SERIES, STUDY where STUDY.PoolKey = ? and STUDY.StudyKey = ? and SERIES.StudyKey = STUDY.StudyKey order by SERIES.SeriesNumber", new Object[] { poolKey, studyKey }, new RowCallbackHandler() {

      @Override
      public void processRow(ResultSet rs) throws SQLException {
        ObjectNode s = series.addObject();
        s.put("seriesDescription", rs.getString("SeriesDescription"));
        s.put("seriesNumber", rs.getString("SeriesNumber"));
        s.put("seriesKey", rs.getInt("SeriesKey"));
        int seriesKey = rs.getInt("SeriesKey");
        Integer numberOfImages = template.queryForObject("select count(*) from INSTANCE where INSTANCE.SeriesKey =  ?", new Object[] { seriesKey }, Integer.class);
        s.put("numImages", numberOfImages);
      }
    });

    for (Iterator<JsonNode> elements = series.elements(); elements.hasNext();) {
      ObjectNode s = (ObjectNode) elements.next();
      // Fill in the instances
      final ArrayNode instances = s.putArray("instanceList");
      int seriesKey = s.get("seriesKey").asInt();
      // Order by InstanceNumber, if it's null, return '0' and cast to integer
      template.query("select * from INSTANCE where SeriesKey = ? order by cast ( NULLIF(InstanceNumber,'0') as INT )", new Object[] { seriesKey }, new RowCallbackHandler() {

        @Override
        public void processRow(ResultSet rs) throws SQLException {
          ObjectNode instance = instances.addObject();
          String imageID = "image/" + rs.getString("FilePath");
          instance.put("imageId", imageID);
          instance.put("uri", "dicomweb:/rest/pool/" + poolKey + "/viewer/" + imageID);
        }
      });
    }
    return Response.ok(json).build();
  }

  @GET
  @Path("image/{path:.+}")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public Response getInstance(@Auth Subject subject, @PathParam("path") String path) {
    logger.debug("Looking for image: " + path);
    PoolContainer poolContainer = poolManager.getContainer(poolKey);
    if (poolContainer != null) {
      File imageFile = new File(poolContainer.getPoolDirectory(), path);
      if (imageFile.exists()) {
        return Response.ok(imageFile).header("Content-Disposition", "attachment; filename=" + imageFile.getName()).build();
      }
    }
    return Response.status(Status.NOT_FOUND).build();
  }
}
