package edu.mayo.qia.pacs.rest;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.annotation.RequiresGuest;
import org.apache.shiro.crypto.hash.SimpleHash;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ByteSource;
import org.secnod.shiro.jaxrs.Auth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Iterator;
import java.util.Random;
import java.util.UUID;

import edu.mayo.qia.pacs.NotionConfiguration;
import edu.mayo.qia.pacs.components.User;
import edu.mayo.qia.pacs.db.UserDAO;
import io.dropwizard.hibernate.UnitOfWork;

@Path("/user")
@Component
@Scope("singleton")
public class UserEndpoint {
  static Logger logger = LoggerFactory.getLogger(UserEndpoint.class);

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  UserDAO userDAO;

  @Autowired
  JdbcTemplate template;

  @Autowired
  NotionConfiguration configuration;

  Random rng = new SecureRandom();

  @GET
  @UnitOfWork
  @Produces(MediaType.APPLICATION_JSON)
  public Response checkLogin(@Auth Subject subject) {
    User user = userDAO.getFromSubject(subject);
    if (user == null) {
      user = new User();
    }
    ObjectNode json = objectMapper.createObjectNode();
    json.putPOJO("user", user);
    json.put("isAuthenticated", subject.isAuthenticated());
    json.put("isRemembered", subject.isRemembered());
    json.put("allowRegistration", configuration.notion.allowRegistration);
    return Response.ok(json).build();
  }

  @POST
  @UnitOfWork
  @Path("/logout")
  public Response logout(@Auth Subject subject) {
    subject.logout();
    return Response.ok().build();
  }

  @POST
  @Path("login")
  @UnitOfWork
  @Consumes(MediaType.APPLICATION_JSON)
  public Response login(JsonNode credentials, @Auth Subject subject) {
    String username = credentials.path("username").asText();
    String password = credentials.path("password").asText();
    boolean remember = credentials.path("remember").booleanValue();
    if (username.isEmpty() || password.isEmpty()) {
      return Response.serverError().entity("username and password required").build();
    }
    try {
      subject.login(new UsernamePasswordToken(username, password, remember));
    } catch (AuthenticationException e) {
      return Response.serverError().entity(new SimpleResponse("message", "Login failed")).build();
    }
    User user = userDAO.getFromSubject(subject);
    if (user == null) {
      // Create a new user. This assumes Shiro is using something like
      // ActiveDirectory...
      user = new User();
      user.username = subject.getPrincipal().toString();
      userDAO.create(user);
    }
    return checkLogin(subject);
  }

  @POST
  @Path("permission")
  @UnitOfWork
  @Produces(MediaType.APPLICATION_JSON)
  public Response checkPermissions(JsonNode check, @Auth Subject subject) {
    ObjectNode permissions = objectMapper.createObjectNode();

    if (check.with("permission").isContainerNode()) {
      ObjectNode list = (ObjectNode) check.with("permission");
      Iterator<String> it = list.fieldNames();
      while (it.hasNext()) {
        String name = it.next();
        permissions.put(name, subject.isPermitted(list.get(name).asText()));
      }
    }
    return Response.ok(permissions).build();
  }

  @UnitOfWork
  @PUT
  @Path("/update")
  public Response update(@Auth Subject subject, User update) {
    User user = userDAO.getFromSubject(subject);
    user.email = update.email;
    userDAO.update(user);
    return Response.ok(user).build();
  }

  @UnitOfWork
  @RequiresGuest
  @POST
  @Path("/register")
  @Produces(MediaType.APPLICATION_JSON)
  public Response register(@FormParam("username") String username, @FormParam("email") String email, @FormParam("password") String password, @Auth Subject subject) {
    // Note that a normal app would reference an attribute rather
    // than create a new RNG every time:

    if (username == null || email == null || password == null) {
      return Response.status(Status.BAD_REQUEST).entity(new SimpleResponse("message", "Must provide username, email and password")).build();
    }

    String salt = Long.toString(rng.nextLong());

    User user = new User();
    user.username = username;
    user.email = username;
    // Now hash the plain-text password with the random salt and multiple
    // iterations and then Base64-encode the value (requires less space than
    // Hex):
    user.password = new SimpleHash(configuration.notion.hashAlgorithm, password, ByteSource.Util.bytes(salt), configuration.notion.hashIterations).toHex();
    // save the salt with the new account. The HashedCredentialsMatcher
    // will need it later when handling login attempts:
    user.salt = salt;
    user.activated = false;
    user.activationHash = UUID.randomUUID().toString();

    // Make the first user an admin
    Integer userCount = template.queryForObject("select count(*) from USERS", Integer.class);
    if (userCount == 0) {
      user.isAdmin = true;
    }

    try {
      user = userDAO.create(user);
    } catch (Exception e) {
      return Response.status(Status.BAD_REQUEST).entity(new SimpleResponse("message", "Failed to create user")).build();
    }
    userDAO.commit();
    try {
      subject.login(new UsernamePasswordToken(username, password));
    } catch (AuthenticationException e) {
      logger.error("Error registering in", e);
    }

    return checkLogin(subject);
  }
}
