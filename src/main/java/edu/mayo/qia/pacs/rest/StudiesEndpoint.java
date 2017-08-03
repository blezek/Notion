package edu.mayo.qia.pacs.rest;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.jersey.spi.resource.PerRequest;

import org.apache.log4j.Logger;
import org.apache.shiro.subject.Subject;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.joda.time.DateTime;
import org.secnod.shiro.jaxrs.Auth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import edu.mayo.qia.pacs.Audit;
import edu.mayo.qia.pacs.components.Instance;
import edu.mayo.qia.pacs.components.Pool;
import edu.mayo.qia.pacs.components.PoolManager;
import edu.mayo.qia.pacs.components.Series;
import edu.mayo.qia.pacs.components.Study;
import io.dropwizard.hibernate.UnitOfWork;

@Scope("prototype")
@Component
@PerRequest
public class StudiesEndpoint {
  static Logger logger = Logger.getLogger(ScriptEndpoint.class);
  public static final String regex = "[^0-9a-zA-Z_-]";

  @Autowired
  SessionFactory sessionFactory;

  @Autowired
  JdbcTemplate template;

  @Autowired
  PoolManager poolManager;

  public int poolKey;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response searchStudies(@Auth final Subject subject, @Context UriInfo uriInfo, JsonNode qParams) throws Exception {
    final Set<String> columns = new HashSet<String>(Arrays.asList(new String[] { "PatientID", "PatientName", "AccessionNumber", "StudyDescription" }));
    final Set<String> directions = new HashSet<String>();
    directions.add("ASC");
    directions.add("DESC");
    ObjectNode json = new ObjectMapper().createObjectNode();
    json.put("Result", "OK");
    // Build the query
    MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();

    logger.debug("query parameters\n" + queryParameters);

    logger.debug("query object\n" + qParams);

    StringBuilder query = new StringBuilder("select * from STUDY where PoolKey = ?");
    ArrayList<Object> parameters = new ArrayList<Object>();
    parameters.add(poolKey);

    StringBuilder where = new StringBuilder(" ");

    for (String column : columns) {
      if (qParams.has(column)) {
        where.append(" and upper(" + column + ") like upper(?) ");
        parameters.add("%" + qParams.get(column).textValue() + "%");
      }
    }

    if (qParams.has("jtSorting")) {
      query.append(" ORDER BY ");

      for (String clause : qParams.get("jtSorting").textValue().split(",")) {
        String[] p = clause.split("\\s+");
        if (columns.contains(p[0])) {
          where.append(p[0]);
        }
        if (directions.contains(p[1])) {
          where.append(" " + p[1]);
        }
      }
    }

    // do our count here:
    Integer count = template.queryForObject("select count(*) from STUDY where PoolKey = ? " + where, parameters.toArray(), Integer.class);
    // Also need to return the total number of records
    json.put("TotalRecordCount", count);
    query.append(where);
    // jtStartIndex: Start index of records for current page.
    // jtPageSize: Count of maximum expected records.
    if (qParams.has("jtStartIndex") && qParams.has("jtPageSize")) {
      query.append(" OFFSET ? ROWS ");
      parameters.add(qParams.get("jtStartIndex").asInt(0));
      query.append(" FETCH NEXT ? ROWS ONLY ");
      parameters.add(qParams.get("jtPageSize").asInt(50));
    }
    final ArrayNode records = json.putArray("Records");
    template.query(query.toString(), parameters.toArray(), new RowCallbackHandler() {

      @Override
      public void processRow(ResultSet rs) throws SQLException {
        ObjectNode row = records.addObject();
        for (String column : columns) {
          row.put(column, rs.getString(column));
        }
        for (String column : new String[] { "StudyKey" }) {
          row.put(column, rs.getInt(column));
        }
        Audit.log(subject, "view_study", row);
      }
    });

    return Response.ok(json).build();
  }

  @GET
  @Path("/{id: [1-9][0-9]*}/hash")
  @UnitOfWork
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response getHash(final @Auth Subject subject, @PathParam("id") final int id) throws Exception {
    // Create a hash value that expires in 30 days

    String hash = UUID.randomUUID().toString();
    DateTime dt = new DateTime().plusDays(30);
    template.update("insert into VIEWERHASH ( StudyKey, PoolKey, Hash, Expires ) values ( ?, ?, ?, ? )", id, poolKey, hash, new Timestamp(dt.getMillis()));

    ObjectNode json = new ObjectMapper().createObjectNode();
    json.put("Result", "OK");
    json.put("Hash", hash);
    json.put("Expires", dt.toString());
    return Response.ok(json).build();
  }

  @GET
  @Path("/zip")
  @UnitOfWork
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public Response getZip(final @Auth Subject subject, @Context UriInfo uriInfo, @DefaultValue("%") @QueryParam("PatientID") final String PatientID, @DefaultValue("%") @QueryParam("PatientName") final String PatientName,
      @DefaultValue("%") @QueryParam("AccessionNumber") final String AccessionNumber, @DefaultValue("%") @QueryParam("StudyDescription") final String StudyDescription) throws Exception {
    final Pool pool = poolManager.getContainer(poolKey).getPool();
    StringBuilder fn = new StringBuilder(pool.name.replaceAll(regex, "_"));
    fn.append("-StudyDownload-" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
    final String path = fn.toString();
    StreamingOutput stream = new StreamingOutput() {
      @SuppressWarnings("unchecked")
      @Override
      public void write(OutputStream output) throws IOException {
        Session session = sessionFactory.openSession();
        try {
          Query query = session.createQuery("from Study where PoolKey = :poolkey and PatientID like :PatientID and PatientName like :PatientName and AccessionNumber like :AccessionNumber and StudyDescription like :StudyDescription");
          query.setInteger("poolkey", poolKey);
          query.setParameter("PatientID", PatientID);
          query.setParameter("PatientName", PatientName);
          query.setParameter("AccessionNumber", AccessionNumber);
          query.setParameter("StudyDescription", StudyDescription);
          ZipOutputStream zip = new ZipOutputStream(output);
          File poolRootDir = poolManager.getContainer(poolKey).getPoolDirectory();
          // Put the path to make a directory
          zip.putNextEntry(new ZipEntry(path));
          zip.closeEntry();

          for (Study study : (List<Study>) query.list()) {
            appendStudyToZip(path, zip, poolRootDir, study);
            Audit.log(subject, "download_study", study.toJson());
          }
          zip.close();

        } finally {
          session.close();
        }
      }
    };
    fn.append(".zip");
    return Response.ok(stream).header("content-disposition", "attachment; filename = " + fn).build();
  };

  // Get as a ZIP file
  @GET
  @UnitOfWork
  @Path("/{id: [1-9][0-9]*}/zip")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public Response getZip(@Auth final Subject subject, @PathParam("id") final int id) throws Exception {
    Query query;
    Session session = sessionFactory.getCurrentSession();
    query = session.createQuery("from Study where PoolKey = :poolkey and StudyKey = :id");
    query.setInteger("poolkey", poolKey);
    query.setInteger("id", id);
    final Study study = (Study) query.uniqueResult();
    if (study == null) {
      return Response.status(Status.NOT_FOUND).build();
    }
    StreamingOutput stream = new StreamingOutput() {
      @Override
      public void write(OutputStream output) throws IOException {
        Session session = sessionFactory.openSession();
        try {
          Query query = session.createQuery("from Study where PoolKey = :poolkey and StudyKey = :id");
          query.setInteger("poolkey", poolKey);
          query.setInteger("id", id);
          final Study study = (Study) query.uniqueResult();
          Audit.log(subject, "download_study", study.toJson());

          ZipOutputStream zip = new ZipOutputStream(output);
          File poolRootDir = poolManager.getContainer(poolKey).getPoolDirectory();
          appendStudyToZip("", zip, poolRootDir, study);
          zip.close();
        } finally {
          session.close();
        }
      }

    };
    StringBuilder fn = new StringBuilder(study.PatientName.replaceAll(regex, "_"));
    fn.append("-").append(study.StudyDate == null ? "empty" : study.StudyDate.toString().replaceAll(regex, "_"));
    fn.append("-").append(study.StudyDescription == null ? "empty" : study.StudyDescription.replaceAll(regex, "_"));
    fn.append(".zip");
    return Response.ok(stream).header("content-disposition", "attachment; filename = " + fn).build();
  }

  /** Delete a Study. */
  @DELETE
  @Path("/{id: [1-9][0-9]*}")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response deleteScript(@PathParam("id") int id) {
    if (poolManager.getContainer(poolKey) != null) {
      if (poolManager.getContainer(poolKey).deleteStudy(id)) {
        return Response.ok().build();
      } else {
        return Response.status(Status.NOT_FOUND).build();
      }
    }
    return Response.status(Status.NOT_FOUND).build();
  }

  public static void appendStudyToZip(String basePath, ZipOutputStream zip, File poolRootDir, final Study study) throws IOException, FileNotFoundException {
    byte[] buffer = new byte[1024];
    String path = study.PatientName == null ? "UnknownPatientName" : study.PatientName.replaceAll(regex, "_");

    String sub = study.StudyDescription == null ? "EmptyStudyDescription" : study.StudyDescription.replaceAll(regex, "_") + "-";
    DateFormat format = new SimpleDateFormat("yyyy-MM-dd");
    path = basePath + path + "/" + sub + format.format(study.StudyDate);
    // zip.putNextEntry(new ZipEntry(path));
    for (Series series : study.series) {
      String desc = series.SeriesDescription == null ? "EmptySeriesDescription" : series.SeriesDescription;
      String seriesPath = path + "/" + desc.replaceAll(regex, "_");
      // zip.putNextEntry(new ZipEntry(seriesPath));
      for (Instance instance : series.instances) {
        String instancePath = seriesPath + "/" + instance.SOPInstanceUID + ".dcm";
        zip.putNextEntry(new ZipEntry(instancePath));
        File f = new File(poolRootDir, instance.FilePath);
        // Read into the zip file
        FileInputStream in = new FileInputStream(f);
        int len;
        while ((len = in.read(buffer)) > 0) {
          zip.write(buffer, 0, len);
        }
        in.close();
        zip.closeEntry();
      }
    }
    zip.closeEntry();
  }
}
