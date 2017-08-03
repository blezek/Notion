package edu.mayo.qia.pacs.rest;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.core.ResourceContext;

import org.apache.log4j.Logger;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import edu.mayo.qia.pacs.components.Connector;
import edu.mayo.qia.pacs.components.PoolManager;
import io.dropwizard.hibernate.UnitOfWork;

@Component
@Path("/connector")
@Scope("singleton")
public class ConnectorEndpoint {
  static Logger logger = Logger.getLogger(ConnectorEndpoint.class);

  @Autowired
  JdbcTemplate template;

  @Autowired
  SessionFactory sessionFactory;

  @Autowired
  PoolManager poolManager;

  @Autowired
  ObjectMapper objectMapper;

  @Context
  ResourceContext resourceContext;

  @SuppressWarnings("unchecked")
  @GET
  @UnitOfWork
  @Produces(MediaType.APPLICATION_JSON)
  public Response getConnector() {
    List<Connector> result = new ArrayList<Connector>();
    Session session = sessionFactory.getCurrentSession();
    result = session.createCriteria(Connector.class).list();
    SimpleResponse s = new SimpleResponse("connector", result);
    return Response.ok(s).build();
  }

  @POST
  @UnitOfWork
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RequiresPermissions({ "admin" })
  public Response createConnector(Connector connector) {
    Session session = sessionFactory.getCurrentSession();
    session.save(connector);
    return Response.ok(connector).build();
  }

  @PUT
  @Path("/{id: [1-9][0-9]*}")
  @UnitOfWork
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RequiresPermissions({ "admin" })
  public Response updateConnector(@PathParam("id") int id, Connector connector) {
    Session session = sessionFactory.getCurrentSession();
    session.update(connector);
    return Response.ok(connector).build();
  }

  @DELETE
  @Path("/{id: [1-9][0-9]*}")
  @Produces(MediaType.APPLICATION_JSON)
  @RequiresPermissions({ "admin" })
  public Response deleteConnector(Connector connector) {
    Session session = sessionFactory.getCurrentSession();
    session.delete(connector);
    return Response.ok().build();
  }
}
