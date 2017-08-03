package edu.mayo.qia.pacs.rest;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.jersey.api.client.ClientResponse.Status;
import com.sun.jersey.spi.resource.PerRequest;

import org.apache.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import edu.mayo.qia.pacs.components.Device;
import edu.mayo.qia.pacs.components.Pool;
import io.dropwizard.hibernate.UnitOfWork;

// Create a new one every time
/*
 @Component
 */
@Scope("prototype")
@Component
@PerRequest
public class DeviceEndpoint {
  static Logger logger = Logger.getLogger(DeviceEndpoint.class);

  @Autowired
  SessionFactory sessionFactory;

  @Autowired
  JdbcTemplate template;

  @Autowired
  ObjectMapper objectMapper;

  public int poolKey;

  /** List all the devices. */
  @GET
  @UnitOfWork
  @Produces(MediaType.APPLICATION_JSON)
  public Response listDevices() {
    // Look up the pool and change it
    Pool pool = null;
    Session session = sessionFactory.getCurrentSession();
    pool = (Pool) session.byId(Pool.class).load(poolKey);
    if (pool == null) {
      return Response.status(Status.NOT_FOUND).entity(new SimpleResponse("message", "Could not load the pool")).build();
    }
    // Force load
    pool.getDevices().size();
    SimpleResponse s = new SimpleResponse("device", pool.getDevices());
    return Response.ok(s).build();
  }

  /** Get an individual Device. */
  @GET
  @Path("/{id: [1-9][0-9]*}")
  @UnitOfWork
  @Produces(MediaType.APPLICATION_JSON)
  public Response getDevice(@PathParam("id") int id) {
    // Look up the pool and change it
    Session session = sessionFactory.getCurrentSession();
    Device device = null;
    device = (Device) session.byId(Device.class).load(id);
    if (device == null) {
      return Response.status(Status.NOT_FOUND).entity(new SimpleResponse("message", "Could not load the device")).build();
    }
    if (device.pool.poolKey != poolKey) {
      return Response.status(Status.NOT_FOUND).entity(new SimpleResponse("message", "Could not load the device")).build();
    }
    return Response.ok(device).build();
  }

  /** Does a DICOM triplet match any devices? */
  @POST
  @Path("match")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response testDevice(Device device) {
    ObjectNode json = objectMapper.createObjectNode();
    final String remoteHostName = device.hostName;
    final String callingAET = device.applicationEntityTitle;
    final int remotePort = device.port;
    final ArrayNode devices = json.putArray("device");
    final AtomicBoolean canQuery = new AtomicBoolean(false);
    final AtomicBoolean canRetreve = new AtomicBoolean(false);

    template.query("select Device.ApplicationEntityTitle AS AET, Device.HostName AS HN, Device.Port AS P from Device where Device.PoolKey = ?", new Object[] { poolKey }, new RowCallbackHandler() {

      @Override
      public void processRow(ResultSet rs) throws SQLException {
        ObjectNode d = devices.addObject();
        String AET = rs.getString("AET");
        String HN = rs.getString("HN");
        int P = rs.getInt("P");
        d.put("applicationEntityTitle", AET);
        d.put("hostName", HN);
        d.put("port", P);
        d.put("name", AET + "@" + HN + ":" + P);
        boolean matchHostName = Pattern.compile(HN, Pattern.CASE_INSENSITIVE).matcher(remoteHostName).matches();
        boolean matchAETitle = Pattern.compile(AET).matcher(callingAET).matches();

        boolean query = matchHostName && matchAETitle;
        boolean retrieve = remoteHostName.equalsIgnoreCase(HN) && callingAET.equals(AET) && remotePort == P;
        d.put("query", query);
        d.put("store", query);
        d.put("retrieve", retrieve);
        canQuery.compareAndSet(false, query);
        canRetreve.compareAndSet(false, retrieve);
      }
    });
    json.put("query", canQuery.get());
    json.put("store", canQuery.get());
    json.put("retrieve", canRetreve.get());
    return Response.ok(json).build();
  }

  /** Create a Device. */
  @POST
  @UnitOfWork
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response createDevice(Device device) {
    // Look up the pool and change it
    Session session = sessionFactory.getCurrentSession();
    Pool pool = (Pool) session.byId(Pool.class).getReference(poolKey);
    // Ensure the device does not think it already exists!
    device.deviceKey = -1;
    device.setPool(pool);
    pool.getDevices().add(device);
    return Response.ok(device).build();
  }

  /** Modify a Device. */
  @PUT
  @Path("/{id: [1-9][0-9]*}")
  @UnitOfWork
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response modifyDevice(@PathParam("id") int id, Device update) {
    // Look up the pool and change it
    Session session = sessionFactory.getCurrentSession();
    Device device = null;
    device = (Device) session.byId(Device.class).load(id);
    if (device == null) {
      return Response.status(Status.NOT_FOUND).entity(new SimpleResponse("message", "Could not load the device")).build();
    }
    if (device.pool.poolKey != poolKey) {
      return Response.status(Status.NOT_FOUND).entity(new SimpleResponse("message", "Could not load the device")).build();
    }
    device.update(update);
    return Response.ok(device).build();
  }

  /** Delete a Device. */
  @DELETE
  @Path("/{id: [1-9][0-9]*}")
  @UnitOfWork
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response deleteDevice(@PathParam("id") int id) {
    // Look up the pool and change it
    Session session = sessionFactory.getCurrentSession();
    Device device;
    device = (Device) session.byId(Device.class).getReference(id);
    if (device.pool.poolKey != poolKey) {
      return Response.status(Status.NOT_FOUND).entity(new SimpleResponse("message", "Could not load the device")).build();
    }
    session.delete(device);
    SimpleResponse response = new SimpleResponse();
    response.put("status", "success");
    response.put("message", "Delete device " + device.deviceKey);
    return Response.ok(response).build();
  }
}
