package edu.mayo.qia.pacs.rest;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.jersey.api.client.ClientResponse.Status;
import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;
import com.sun.jersey.spi.resource.PerRequest;

import org.apache.log4j.Logger;
import org.apache.shiro.subject.Subject;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.secnod.shiro.jaxrs.Auth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import edu.mayo.qia.pacs.Audit;
import edu.mayo.qia.pacs.components.Connector;
import edu.mayo.qia.pacs.components.Device;
import edu.mayo.qia.pacs.components.Item;
import edu.mayo.qia.pacs.components.Pool;
import edu.mayo.qia.pacs.components.PoolManager;
import edu.mayo.qia.pacs.components.Query;
import edu.mayo.qia.pacs.components.Result;
import edu.mayo.qia.pacs.components.Study;
import io.dropwizard.hibernate.UnitOfWork;

@Scope("prototype")
@Component
@PerRequest
public class QueryEndpoint {
  static Logger logger = Logger.getLogger(QueryEndpoint.class);

  @Autowired
  SessionFactory sessionFactory;

  @Autowired
  JdbcTemplate template;

  @Autowired
  PoolManager poolManager;

  public int poolKey;

  @GET
  @UnitOfWork
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response getQueries(@Auth Subject subject) {

    Pool pool = (Pool) sessionFactory.getCurrentSession().byId(Pool.class).load(poolKey);
    if (pool == null) {
      return Response.status(Status.NOT_FOUND).entity(new SimpleResponse("message", "Could not load the pool")).build();
    }
    // make sure all the Queries are fetched from the DB
    pool.queries.size();
    return Response.ok(new SimpleResponse("queries", pool.queries)).build();
  }

  @PUT
  @Path("/{id: [1-9][0-9]*}")
  @UnitOfWork
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response update(@PathParam("id") int id, Query update) {
    Session session = sessionFactory.getCurrentSession();
    Query query;
    query = (Query) session.byId(Query.class).load(id);
    if (query == null || query.pool.poolKey != poolKey) {
      return Response.status(Status.NOT_FOUND).entity(new SimpleResponse("message", "Could not load the query")).build();
    }
    query.update(update);
    return getQuery(id);
  }

  @PUT
  @Path("/{id: [1-9][0-9]*}/fetch")
  @UnitOfWork
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response fetch(@PathParam("id") int id) {
    Session session = sessionFactory.getCurrentSession();
    Query query;
    query = (Query) session.byId(Query.class).load(id);
    if (query == null || query.pool.poolKey != poolKey) {
      return Response.status(Status.NOT_FOUND).entity(new SimpleResponse("message", "Could not load the query")).build();
    }
    query.doFetch();
    return getQuery(id);
  }

  @PUT
  @Path("/{id: [1-9][0-9]*}/query")
  @UnitOfWork
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response query(@PathParam("id") int id) {
    Session session = sessionFactory.getCurrentSession();
    Query query;
    query = (Query) session.byId(Query.class).load(id);
    if (query == null || query.pool.poolKey != poolKey) {
      return Response.status(Status.NOT_FOUND).entity(new SimpleResponse("message", "Could not load the query")).build();
    }
    query.status = "starting";
    query.executeQuery();
    return getQuery(id);
  }

  @GET
  @Path("/{id: [1-9][0-9]*}/zip")
  @UnitOfWork
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public Response getZipForFetch(final @Auth Subject subject, @PathParam("id") final int id) {
    Pool pool = (Pool) sessionFactory.getCurrentSession().byId(Pool.class).load(poolKey);
    StringBuilder fn = new StringBuilder(pool.name.replaceAll(StudiesEndpoint.regex, "_"));
    fn.append("-Fetch-" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()));

    final String base = fn.toString();

    StreamingOutput stream = new StreamingOutput() {
      @Override
      public void write(OutputStream output) throws IOException {
        Session session = sessionFactory.openSession();
        Set<Integer> zippedStudies = new HashSet<Integer>();
        try {
          Query query;
          query = (Query) session.byId(Query.class).load(id);

          ZipOutputStream zip = new ZipOutputStream(output);
          File poolRootDir = poolManager.getContainer(poolKey).getPoolDirectory();
          String path = base + "/";
          // Put the path to make a directory
          zip.putNextEntry(new ZipEntry(path));
          zip.closeEntry();

          // Find every result and append
          for (Item item : query.items) {
            for (Result result : item.items) {
              logger.error("Looknig at StudyKey: " + result.studyKey);
              if (result.doFetch && result.studyKey != null && !zippedStudies.contains(result.studyKey)) {
                // Add it
                zippedStudies.add(result.studyKey);
                org.hibernate.Query q = session.createQuery("from Study where PoolKey = :poolkey and StudyKey = :id");
                q.setInteger("poolkey", poolKey);
                q.setInteger("id", result.studyKey);
                final Study study = (Study) q.uniqueResult();
                if (study != null) {
                  Audit.log(subject.getPrincipal().toString(), "download_study", study.toJson());

                  StudiesEndpoint.appendStudyToZip(path, zip, poolRootDir, study);
                }
              }
            }
          }
          zip.close();
        } catch (Exception e) {
          logger.error("Error constructing zip file", e);
        } finally {
          session.close();
        }
      }
    };

    return Response.ok(stream).header("content-disposition", "attachment; filename = " + fn + ".zip").build();
  }

  void linkQuery(Connector connector, Query query) throws Exception {
    Session session = sessionFactory.getCurrentSession();

    int destinationPoolKey = connector.destinationPoolKey;
    int deviceKey = connector.queryDeviceKey;
    int queryPoolKey = connector.queryPoolKey;

    Pool pool = (Pool) session.byId(Pool.class).load(poolKey);
    if (pool == null) {
      throw new Exception("Could not load the pool");
    }

    Pool queryPool = (Pool) session.byId(Pool.class).load(queryPoolKey);
    if (queryPool == null) {
      throw new Exception("Could not load the query Pool");
    }

    Pool destinationPool = (Pool) session.byId(Pool.class).load(destinationPoolKey);
    if (destinationPool == null) {
      throw new Exception("Could not load the destination");
    }

    Device device = (Device) session.byId(Device.class).load(deviceKey);

    if (device == null || device.pool.poolKey != queryPoolKey) {
      throw new Exception("Could not load the device");
    }
    query.pool = pool;
    query.device = device;
    query.destinationPool = destinationPool;
    query.queryPool = queryPool;
  }

  @PUT
  @Path("/simple")
  @UnitOfWork
  public Response createQuery(JsonNode json) {
    Session session = sessionFactory.getCurrentSession();
    try {
      Connector connector = (Connector) session.byId(Connector.class).load(json.get("connectorKey").asInt());
      Query query = Query.constructQuery(json);
      try {
        linkQuery(connector, query);
      } catch (Exception e) {
        return Response.status(Status.NOT_FOUND).entity(new SimpleResponse("message", e.getMessage())).build();
      }
      session.save(query);
      session.getTransaction().commit();
      query.executeQuery();
      return Response.ok(query).build();
    } catch (Exception e) {
      return Response.status(Status.INTERNAL_SERVER_ERROR).entity(new SimpleResponse("message", "Failed to process simple query")).build();
    }
  }

  @POST
  @UnitOfWork
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Produces(MediaType.APPLICATION_JSON)
  public Response createQuery(@FormDataParam("file") InputStream spreadSheetInputStream, @FormDataParam("file") FormDataContentDisposition fileDetail, @FormDataParam("connectorKey") int connectorKey) throws Exception {
    logger.debug("handling  " + fileDetail.getFileName());
    // Use POI or SuperCSV to parse
    fileDetail.getFileName();
    if (fileDetail.getFileName().toLowerCase().endsWith(".xls")) {
      // Parse and create a query
    }
    Session session = sessionFactory.getCurrentSession();
    Query query;
    Connector connector = (Connector) session.byId(Connector.class).load(connectorKey);
    try {
      query = Query.constructQuery(fileDetail.getFileName(), spreadSheetInputStream);
      linkQuery(connector, query);
    } catch (Exception e) {
      return Response.status(Status.NOT_FOUND).entity(new SimpleResponse("message", e.getMessage())).build();
    }
    session.save(query);
    session.getTransaction().commit();
    query.executeQuery();
    return Response.ok(query).build();
  }

  @GET
  @Path("/{id: [1-9][0-9]*}")
  @UnitOfWork
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response getQuery(@PathParam("id") int id) {
    Session session = sessionFactory.getCurrentSession();
    Query query;
    query = (Query) session.byId(Query.class).load(id);
    if (query == null || query.pool.poolKey != poolKey) {
      return Response.status(Status.NOT_FOUND).entity(new SimpleResponse("message", "Could not load the query")).build();
    }
    return Response.ok(query).build();
  }

  @GET
  @Path("/{id: [1-9][0-9]*}/excel")
  @UnitOfWork
  @Consumes(MediaType.APPLICATION_JSON)
  public Response getExcel(@PathParam("id") int id) {
    Session session = sessionFactory.getCurrentSession();
    final Query query;
    query = (Query) session.byId(Query.class).load(id);
    if (query == null || query.pool.poolKey != poolKey) {
      return Response.status(Status.NOT_FOUND).entity(new SimpleResponse("message", "Could not load the query")).build();
    }
    StreamingOutput stream = new StreamingOutput() {
      @Override
      public void write(OutputStream output) throws IOException {
        query.generateSpreadSheet().write(output);
      }
    };

    String fn = poolManager.getContainer(poolKey).getPool().applicationEntityTitle + "-Query-" + new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(new Date()) + ".xlsx";
    return Response.ok(stream).type("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").header("content-disposition", "attachment; filename = " + fn).build();
  }
}
