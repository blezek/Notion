package edu.mayo.qia.pacs.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.mayo.qia.pacs.components.Group;
import edu.mayo.qia.pacs.db.GroupDAO;
import io.dropwizard.hibernate.UnitOfWork;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.subject.Subject;
import org.secnod.shiro.jaxrs.Auth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Component
public class GroupEndpoint {

  @Autowired
  JdbcTemplate template;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  GroupDAO groupDAO;

  @POST
  @UnitOfWork
  @Consumes(MediaType.APPLICATION_JSON)
  @RequiresPermissions({ "admin" })
  public Response createGroup(@Auth Subject subject, Group group) {
    return Response.ok(groupDAO.create(group)).build();
  }

  @GET
  @UnitOfWork
  @Produces(MediaType.APPLICATION_JSON)
  public Response getGroups(@Auth Subject subject) {
    return Response.ok(new SimpleResponse("group", groupDAO.findAll(Group.class))).build();
  }

  @PUT
  @Path("/{id: [1-9][0-9]*}")
  @UnitOfWork
  @Produces(MediaType.APPLICATION_JSON)
  public Response updateGroup(@Auth Subject subject, @PathParam("id") int id, Group group) {
    return Response.ok(groupDAO.update(group)).build();
  }

  @GET
  @Path("/{id: [1-9][0-9]*}")
  @UnitOfWork
  @Produces(MediaType.APPLICATION_JSON)
  @RequiresPermissions({ "admin" })
  public Response getGroup(@Auth Subject subject, @PathParam("id") int id) {
    return Response.ok(groupDAO.get(id)).build();
  }

  @DELETE
  @Path("/{id: [1-9][0-9]*}")
  @UnitOfWork
  @Produces(MediaType.APPLICATION_JSON)
  @RequiresPermissions({ "admin" })
  public Response deleteGroup(@Auth Subject subject, @PathParam("id") int id) {
    groupDAO.delete(groupDAO.get(id));
    return Response.ok(new SimpleResponse("message", "success")).build();
  }

}
