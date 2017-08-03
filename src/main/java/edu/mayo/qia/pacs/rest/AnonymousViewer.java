package edu.mayo.qia.pacs.rest;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Iterator;
import java.util.Optional;

import edu.mayo.qia.pacs.components.PoolContainer;
import edu.mayo.qia.pacs.components.PoolManager;

@Component
@Path("/viewer")
@Scope("singleton")
public class AnonymousViewer {
  private static final String EXPIRED_ERROR = "expired hash code, please request renewed access to this study";

  static Logger logger = Logger.getLogger(AnonymousViewer.class);

  @Autowired
  PoolManager poolManager;

  @Autowired
  JdbcTemplate template;

  @Autowired
  ObjectMapper objectMapper;

  boolean isValidHash(String hash) {
    int count = template.queryForObject("select count(*) from VIEWERHASH where hash = ?", new Object[] { hash }, Integer.class);
    if (count == 1) {
      return true;
    }
    return false;
  }

  Optional<Integer> getPoolKey(String hash) {
    if (isValidHash(hash)) {

      return Optional.of(template.queryForObject("select PoolKey from VIEWERHASH where hash = ?", new Object[] { hash }, Integer.class));
    } else {
      return Optional.empty();
    }
  }

  Optional<Integer> getStudyKey(String hash) {
    if (isValidHash(hash)) {
      return Optional.of(template.queryForObject("select StudyKey from VIEWERHASH where hash = ?", new Object[] { hash }, Integer.class));
    } else {
      return Optional.empty();
    }
  }

  /**
   * Study list in JSON format
   * 
   * Return a list of studies following the Cornerstone example JSON file <code>
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
   * 
   * @param hash
   * @return
   */
  @GET
  @Path("/{hash}/series")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getSeries(@PathParam("hash") final String hash) {

    if (!isValidHash(hash)) {
      return Response.status(Status.NOT_FOUND).entity(EXPIRED_ERROR).build();
    }

    Optional<Integer> poolKey = getPoolKey(hash);
    Optional<Integer> studyKey = getStudyKey(hash);

    if (!poolKey.isPresent()) {
      return Response.status(Status.NOT_FOUND).entity(EXPIRED_ERROR).build();
    }
    if (!studyKey.isPresent()) {
      return Response.status(Status.NOT_FOUND).entity(EXPIRED_ERROR).build();
    }

    final ObjectNode json = objectMapper.createObjectNode();
    final ArrayNode series = json.putArray("seriesList");
    template.query("select * from STUDY where PoolKey = ? and StudyKey = ?", new Object[] { poolKey.get(), studyKey.get() }, rs -> {
      json.put("patientName", rs.getString("PatientName"));
      json.put("patientId", rs.getString("PatientID"));
      json.put("studyDate", rs.getString("StudyDate"));
      json.put("modality", "unknown");
      json.put("studyDescription", rs.getString("StudyDescription"));
      json.put("studyId", rs.getString("StudyKey"));
    });

    template.query("select SERIES.* from SERIES, STUDY where STUDY.PoolKey = ? and STUDY.StudyKey = ? and SERIES.StudyKey = STUDY.StudyKey order by SERIES.SeriesNumber", new Object[] { poolKey.get(), studyKey.get() }, rs -> {
      ObjectNode s = series.addObject();
      s.put("seriesDescription", rs.getString("SeriesDescription"));
      s.put("seriesNumber", rs.getString("SeriesNumber"));
      s.put("seriesKey", rs.getInt("SeriesKey"));
      int seriesKey = rs.getInt("SeriesKey");
      Integer numberOfImages = template.queryForObject("select count(*) from INSTANCE where INSTANCE.SeriesKey =  ?", new Object[] { seriesKey }, Integer.class);
      s.put("numImages", numberOfImages);
    });

    for (Iterator<JsonNode> elements = series.elements(); elements.hasNext();) {
      ObjectNode s = (ObjectNode) elements.next();
      // Fill in the instances
      final ArrayNode instances = s.putArray("instanceList");
      int seriesKey = s.get("seriesKey").asInt();
      // Order by InstanceNumber, if it's null, return '0' and cast to integer
      template.query("select * from INSTANCE where SeriesKey = ? order by cast ( NULLIF(InstanceNumber,'0') as INT )", new Object[] { seriesKey }, rs -> {
        ObjectNode instance = instances.addObject();
        String imageID = rs.getString("FilePath");
        instance.put("imageId", imageID);
        instance.put("uri", "dicomweb:/rest/viewer/" + hash + "/image/" + imageID);
      });
    }
    return Response.ok(json).build();
  }

  @GET
  @Path("/{hash}/image/{path:.+}")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public Response getInstance(@PathParam("hash") String hash, @PathParam("path") String path) {
    logger.debug("Looking for image: " + path);
    if (!isValidHash(hash)) {
      return Response.status(Status.NOT_FOUND).entity(EXPIRED_ERROR).build();
    }

    Optional<Integer> poolKey = getPoolKey(hash);
    Optional<Integer> studyKey = getStudyKey(hash);

    if (!poolKey.isPresent()) {
      return Response.status(Status.NOT_FOUND).entity(EXPIRED_ERROR).build();
    }
    if (!studyKey.isPresent()) {
      return Response.status(Status.NOT_FOUND).entity(EXPIRED_ERROR).build();
    }

    if (poolKey.isPresent() && poolKey.isPresent()) {

      // Check that our image is in the pool
      Integer count = template.queryForObject("select count(*) " + " from INSTANCE, SERIES, STUDY" + " where 1=1" + " and INSTANCE.FilePath = ?" + " and INSTANCE.SeriesKey = SERIES.SeriesKey" + " and SERIES.StudyKey = STUDY.StudyKey "
          + " and STUDY.StudyKey = ? " + " and STUDY.PoolKey = ?" + "", new Object[] { path, studyKey.get(), poolKey.get() }, Integer.class);
      if (count == 1) {
        PoolContainer poolContainer = poolManager.getContainer(poolKey.get());
        if (poolContainer != null) {
          File imageFile = new File(poolContainer.getPoolDirectory(), path);
          if (imageFile.exists()) {
            return Response.ok(imageFile).header("Content-Disposition", "attachment; filename=" + imageFile.getName()).build();
          }
        }
      }
    }
    return Response.status(Status.NOT_FOUND).build();
  }

}
