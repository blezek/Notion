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

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.subject.Subject;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.secnod.shiro.jaxrs.Auth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

import edu.mayo.qia.pacs.components.GroupRole;
import edu.mayo.qia.pacs.db.GroupRoleDAO;
import io.dropwizard.hibernate.UnitOfWork;

@Component
public class GroupRoleEndpoint {

  @Autowired
  JdbcTemplate template;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  GroupRoleDAO groupRoleDAO;

  @Autowired
  SessionFactory sessionFactory;

  public int poolKey;

  @POST
  @UnitOfWork
  @Consumes(MediaType.APPLICATION_JSON)
  @RequiresPermissions({ "admin" })
  public Response createGroup(@Auth Subject subject, GroupRole group) {
    if (group.poolKey != poolKey) {
      return Response.serverError().entity("Group role is not defined in this pool").build();
    }
    return Response.ok(groupRoleDAO.create(group)).build();
  }

  @GET
  @UnitOfWork
  @Produces(MediaType.APPLICATION_JSON)
  public Response getGroups(@Auth Subject subject) {
    subject.checkPermission("pool:admin:" + poolKey);
    Session session = sessionFactory.getCurrentSession();
    Criteria criteria = session.createCriteria(GroupRole.class).add(Restrictions.eq("poolKey", poolKey));
    @SuppressWarnings("unchecked")
    List<GroupRole> result = criteria.list();
    return Response.ok(new SimpleResponse("groupRole", result)).build();
  }

  @PUT
  @Path("/{id: [1-9][0-9]*}")
  @UnitOfWork
  @Produces(MediaType.APPLICATION_JSON)
  public Response updateGroup(@Auth Subject subject, @PathParam("id") int id, GroupRole group) {
    subject.checkPermission("pool:admin:" + poolKey);
    if (group.poolKey != poolKey) {
      return Response.serverError().entity("Group role is not defined in this pool").build();
    }
    return Response.ok(groupRoleDAO.update(group)).build();
  }

  @GET
  @Path("/{id: [1-9][0-9]*}")
  @UnitOfWork
  @Produces(MediaType.APPLICATION_JSON)
  public Response getGroup(@Auth Subject subject, @PathParam("id") int id) {
    subject.checkPermission("pool:admin:" + poolKey);
    GroupRole group = groupRoleDAO.get(id);
    if (group.poolKey != poolKey) {
      return Response.serverError().entity("Group role is not defined in this pool").build();
    }
    return Response.ok(group).build();
  }

  @DELETE
  @Path("/{id: [1-9][0-9]*}")
  @UnitOfWork
  @Produces(MediaType.APPLICATION_JSON)
  @RequiresPermissions({ "admin" })
  public Response deleteGroup(@Auth Subject subject, @PathParam("id") int id) {
    GroupRole group = groupRoleDAO.get(id);
    if (group.poolKey != poolKey) {
      return Response.serverError().entity("Group role is not defined in this pool").build();
    }
    groupRoleDAO.delete(group);
    return Response.ok(new SimpleResponse("message", "success")).build();
  }

}
