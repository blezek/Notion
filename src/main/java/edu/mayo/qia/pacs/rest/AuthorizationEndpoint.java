package edu.mayo.qia.pacs.rest;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.subject.Subject;
import org.secnod.shiro.jaxrs.Auth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import edu.mayo.qia.pacs.components.User;
import edu.mayo.qia.pacs.db.UserDAO;
import io.dropwizard.hibernate.UnitOfWork;

@Path("authorization")
@Scope("singleton")
@Component
public class AuthorizationEndpoint extends Endpoint {
  public Logger logger = LoggerFactory.getLogger(AuthorizationEndpoint.class);

  @Autowired
  UserDAO userDAO;

  @GET
  @Path("users")
  @UnitOfWork
  public Response checkPermissions(@Auth Subject subject) {
    return Response.ok(new SimpleResponse("users", userDAO.findAll())).build();
  }

  @PUT
  @Path("users/{id: [1-9][0-9]*}")
  @UnitOfWork
  @RequiresPermissions("admin:user:edit")
  public Response updateUser(@PathParam("id") int id, User u) {
    User user = userDAO.get(id);
    user.isAdmin = u.isAdmin;
    userDAO.update(user);
    return Response.ok(new SimpleResponse("message", "success")).build();
  }

  @GET
  @Path("permission/{permission}")
  /** Does this user have a particular permission */
  public Response checkPermission(@Auth Subject subject, @PathParam("permission") String permission) {
    return Response.ok(new SimpleResponse("message", "success")).build();
  }

  @Path("group")
  public GroupEndpoint group() {
    return getResource(GroupEndpoint.class);
  }
}
