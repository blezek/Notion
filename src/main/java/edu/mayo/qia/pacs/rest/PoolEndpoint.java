package edu.mayo.qia.pacs.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.jersey.api.client.ClientResponse.Status;
import edu.mayo.qia.pacs.components.MoveRequest;
import edu.mayo.qia.pacs.components.Pool;
import edu.mayo.qia.pacs.components.PoolManager;
import edu.mayo.qia.pacs.components.PoolContainer;
import edu.mayo.qia.pacs.components.Script;
import edu.mayo.qia.pacs.components.User;
import edu.mayo.qia.pacs.components.Group;
import edu.mayo.qia.pacs.components.GroupRole;
import edu.mayo.qia.pacs.db.GroupDAO;
import edu.mayo.qia.pacs.db.GroupRoleDAO;
import edu.mayo.qia.pacs.db.UserDAO;
import io.dropwizard.hibernate.UnitOfWork;
import org.apache.log4j.Logger;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.subject.Subject;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.secnod.shiro.jaxrs.Auth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
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
import java.io.File;
import java.util.List;

//import edu.mayo.qia.pacs.components.*;

@Component
@Path("/pool")
@Scope("singleton")
public class PoolEndpoint extends Endpoint {
  static Logger logger = Logger.getLogger(PoolEndpoint.class);

  @Autowired
  JdbcTemplate template;

  @Autowired
  SessionFactory sessionFactory;

  @Autowired
  PoolManager poolManager;

  @Autowired
  UserDAO userDAO;

  @Autowired
  GroupDAO groupDAO;

  @Autowired
  GroupRoleDAO groupRoleDAO;

  @Autowired
  ObjectMapper objectMapper;

  /** List all the pools */
  @SuppressWarnings("unchecked")
  @GET
  @UnitOfWork
  @Produces(MediaType.APPLICATION_JSON)
  @RequiresPermissions({ "pool:list" })
  public Response listPools(@Auth Subject subject) {
    List<Pool> result;
    Session session = sessionFactory.getCurrentSession();
    // Two paths here, if the user is an admin, show them everything, otherwise
    // just their pools.
    if (subject.isPermitted("admin:pool")) {
      result = session.createCriteria(Pool.class).list();
    } else {
      // @formatter:off
      String query = 
        "select {p.*} from POOL p, GROUPROLE GR, USERGROUP UG, USERS U " 
        + " where "
        + " U.Username = :username "
        + " and p.PoolKey = GR.PoolKey " 
        + " and GR.GroupKey = UG.GroupKey "
        + " and U.UserKey = UG.UserKey "
        + " and ( GR.IsPoolAdmin or GR.IsCoordinator )";
      // @formatter:on
      result = session.createSQLQuery(query).addEntity("p", Pool.class).setParameter("username", subject.getPrincipal().toString()).list();
    }
    // Test for permission
    ObjectNode json = new ObjectMapper().createObjectNode();
    ArrayNode pools = json.putArray("pool");
    for (Pool p : result) {
      pools.addPOJO(p);
    }
    return Response.ok(json).build();
  }

  /** Get a pool. */
  @GET
  @Path("/{id: [1-9][0-9]*}")
  @UnitOfWork
  @Produces(MediaType.APPLICATION_JSON)
  public Response getPool(@Auth Subject subject, @PathParam("id") int id) {
    subject.checkPermission("pool:query:" + id);
    // Look up the pool and change it
    Pool pool = null;
    Session session = sessionFactory.getCurrentSession();
    pool = (Pool) session.byId(Pool.class).load(id);
    return Response.ok(pool).build();
  }

  @GET
  @Path("/{id: [1-9][0-9]*}/statistics")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getPoolStatistics(@Auth Subject subject, @PathParam("id") int id) {
    subject.checkPermission("pool:query:" + id);
    SimpleResponse s = new SimpleResponse();
    s.put("study", template.queryForObject("select count(STUDY.StudyKey) from STUDY where STUDY.PoolKey = ?", Integer.class, id));
    s.put("series", template.queryForObject("select count(SERIES.SeriesKey) from SERIES, STUDY where STUDY.PoolKey = ? and SERIES.StudyKey = STUDY.StudyKey", Integer.class, id));
    s.put("instance", template.queryForObject("select count(INSTANCE.InstanceKey) from INSTANCE, SERIES, STUDY where STUDY.PoolKey = ? and SERIES.StudyKey = STUDY.StudyKey and INSTANCE.SeriesKey = SERIES.SeriesKey", Integer.class, id));
    return Response.ok(s).build();
  }

  @PUT
  @Path("/{id: [1-9][0-9]*}/move")
  @Produces(MediaType.APPLICATION_JSON)
  @RequiresPermissions("admin:move")
  public Response moveStudies(@Auth Subject subject, @PathParam("id") int id, MoveRequest request) {
    ObjectNode json = new ObjectMapper().createObjectNode();
    // Build the query
    ArrayNode records = json.putArray("Status");
    PoolContainer destinationContainer = poolManager.getContainer(request.destinationPoolKey);
    PoolContainer container = poolManager.getContainer(id);
    if (destinationContainer == null || container == null) {
      return Response.status(Status.NOT_FOUND).entity(new SimpleResponse("Message", "Could not find pool")).build();
    }
    // Find all the files and import
    for (int studyKey : request.studyKeys) {
      ObjectNode studyResult = records.addObject();
      try {
        List<String> filePaths = template.queryForList("select FilePath from INSTANCE, SERIES, STUDY where INSTANCE.SeriesKey = SERIES.SeriesKey and SERIES.StudyKey = STUDY.StudyKey and STUDY.PoolKey = ? and STUDY.StudyKey = ?",
            new Object[] { id, studyKey }, String.class);
        for (String file : filePaths) {
          destinationContainer.importFromPool(new File(container.getPoolDirectory(), file));
        }
        studyResult.put("Status", "success");
        studyResult.put("Message", "success");
      } catch (Exception e) {
        studyResult.put("Status", "failed");
        studyResult.put("Message", e.getLocalizedMessage());
      }
    }
    json.put("Result", "OK");
    return Response.ok(json).build();
  }

  /** Devices */
  @Path("/{id: [1-9][0-9]*}/device")
  public DeviceEndpoint devices(@Auth Subject subject, @PathParam("id") int id) {
    subject.checkPermission("pool:query:" + id);
    DeviceEndpoint deviceEndpoint;
    deviceEndpoint = getResource(DeviceEndpoint.class);
    deviceEndpoint.poolKey = id;
    return deviceEndpoint;
  }

  /** Studies */
  @Path("/{id: [1-9][0-9]*}/studies")
  public StudiesEndpoint studies(@Auth Subject subject, @PathParam("id") int id) {
    subject.checkPermission("pool:query:" + id);
    StudiesEndpoint studiesEndpoint;
    studiesEndpoint = getResource(StudiesEndpoint.class);
    studiesEndpoint.poolKey = id;
    return studiesEndpoint;
  }

  /** Lookup */
  @Path("/{id: [1-9][0-9]*}/lookup")
  public LookupEndpoint lookup(@Auth Subject subject, @PathParam("id") int id) {
    subject.checkPermission("pool:query:" + id);
    LookupEndpoint endpoint;
    endpoint = getResource(LookupEndpoint.class);
    endpoint.poolKey = id;
    return endpoint;
  }

  /** Scripts */
  @Path("/{id: [1-9][0-9]*}/script")
  public ScriptEndpoint scripts(@Auth Subject subject, @PathParam("id") int id) {
    subject.checkPermission("pool:query:" + id);
    ScriptEndpoint scriptEndpoint;
    scriptEndpoint = getResource(ScriptEndpoint.class);
    scriptEndpoint.poolKey = id;
    return scriptEndpoint;
  }

  /** Query */
  @Path("/{id: [1-9][0-9]*}/query")
  public QueryEndpoint query(@Auth Subject subject, @PathParam("id") int id) {
    subject.checkPermission("pool:coordinator:" + id);
    QueryEndpoint queryEndpoint;
    queryEndpoint = getResource(QueryEndpoint.class);
    queryEndpoint.poolKey = id;
    return queryEndpoint;
  }

  /** Viewer */
  @Path("/{id: [1-9][0-9]*}/viewer")
  public ViewerEndpoint viewer(@Auth Subject subject, @PathParam("id") int id) {
    subject.checkPermission("pool:coordinator:" + id);
    ViewerEndpoint queryEndpoint;
    queryEndpoint = getResource(ViewerEndpoint.class);
    queryEndpoint.poolKey = id;
    return queryEndpoint;
  }

  /** Group / Roles */
  @Path("/{id: [1-9][0-9]*}/grouprole")
  public GroupRoleEndpoint group(@Auth Subject subject, @PathParam("id") int id) {
    subject.checkPermission("pool:admin:" + id);
    GroupRoleEndpoint endpoint;
    endpoint = getResource(GroupRoleEndpoint.class);
    endpoint.poolKey = id;
    return endpoint;
  }

  /** CTP */
  @GET
  @Path("/{id: [1-9][0-9]*}/ctp")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getCTPConfig(@Auth Subject subject, @PathParam("id") int id) {
    subject.checkPermission("pool:admin:" + id);
    PoolContainer poolContainer = poolManager.getContainer(id);
    if (poolContainer == null) {
      return Response.status(Response.Status.FORBIDDEN).entity(new SimpleResponse("message", "Unknown pool")).build();
    }
    try {
      String config = poolContainer.getCTPConfig();
      return Response.ok(new SimpleResponse("script", config)).build();
    } catch (Exception e) {
      logger.error("Failed to read config file for pool", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new SimpleResponse("message", "Failed to read CTP config file for pool")).build();
    }
  }

  /** CTP */
  @PUT
  @Path("/{id: [1-9][0-9]*}/ctp")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response saveCTPConfig(@Auth Subject subject, @PathParam("id") int id, JsonNode json) {
    subject.checkPermission("pool:admin:" + id);
    PoolContainer poolContainer = poolManager.getContainer(id);
    if (poolContainer == null || !json.has("script")) {
      return Response.status(Response.Status.FORBIDDEN).entity(new SimpleResponse("message", "Unknown pool")).build();
    }
    try {
      poolContainer.putCTPConfig(json.get("script").asText());
      return Response.ok(new SimpleResponse("script", json.get("script").asText())).build();
    } catch (Exception e) {
      logger.error("Failed to save config file for pool", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new SimpleResponse("message", "Failed to save CTP config file for pool")).build();
    }
  }

  /** Create a pool. */
  @POST
  @UnitOfWork
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response createPool(@Auth Subject subject, Pool pool) {

    if (pool.name == null || pool.description == null || pool.applicationEntityTitle == null) {
      return Response.status(Response.Status.FORBIDDEN).entity(new SimpleResponse("message", "ApplicationEntityTitle, Name and Description must not be empty")).build();
    }

    // Does the name conform to what we expect?
    if (!pool.applicationEntityTitle.matches("[a-zA-Z_\\-0-9]+")) {
      return Response.status(Response.Status.FORBIDDEN).entity(new SimpleResponse("message", "ApplicationEntityTitle must consist of letters, numbers dashes, and underscores only")).build();
    }
    // Does the name conform to what we expect?
    if (pool.applicationEntityTitle.length() > 16) {
      return Response.status(Response.Status.FORBIDDEN).entity(new SimpleResponse("message", "ApplicationEntityTitle must be less than 16 characters: " + pool.applicationEntityTitle + " is " + pool.applicationEntityTitle.length() + " characters long"))
          .build();
    }

    Session session = sessionFactory.getCurrentSession();
    Script s;
    s = new Script(Script.createDefaultScript());
    pool.script = s;
    s.setPool(pool);

    session.save(pool);
    poolManager.newPool(pool);

    // Create a group, and add this user to it
    Group g = new Group();
    g.description = pool.name + " -- Administrators";
    g.name = "Administrative group for " + pool.name;
    User user = userDAO.getFromSubject(subject);
    g.userKeys.add(user.userKey);
    groupDAO.create(g);
    GroupRole gr = new GroupRole();
    gr.poolKey = pool.poolKey;
    gr.groupKey = g.groupKey;
    gr.isPoolAdmin = true;
    groupRoleDAO.create(gr);

    return Response.ok(pool).build();
  }

  /** Modify a pool. */
  @PUT
  @Path("/{id: [1-9][0-9]*}")
  @UnitOfWork
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response modifyPool(@Auth Subject subject, @PathParam("id") int id, Pool update) {
    subject.checkPermission("pool:admin:" + id);
    // Look up the pool and change it
    Pool pool = null;
    Session session = sessionFactory.getCurrentSession();
    pool = (Pool) session.byId(Pool.class).load(id);
    if (pool == null) {
      return Response.status(Status.NOT_FOUND).entity(new SimpleResponse("message", "Could not load the pool")).build();
    }
    if (!pool.applicationEntityTitle.equals(update.applicationEntityTitle)) {
      return Response.status(Response.Status.FORBIDDEN).entity(new SimpleResponse("message", "ApplicationEntityTitle can not be changed existing: " + pool.applicationEntityTitle + " attepted change: " + update.applicationEntityTitle)).build();

    }
    // Update the pool
    pool.update(update);
    session.update(pool);
    poolManager.update(pool);
    return Response.ok(pool).build();
  }

  /** Delete a pool. */
  @DELETE
  @Path("/{id: [1-9][0-9]*}")
  @UnitOfWork
  @RequiresPermissions({ "admin:delete" })
  public Response modifyPool(@Auth Subject subject, @PathParam("id") int id) {
    // Look up the pool and change it
    Session session = sessionFactory.getCurrentSession();
    Pool pool = (Pool) session.byId(Pool.class).load(id);
    if (pool == null) {
      return Response.status(Status.NOT_FOUND).entity(new SimpleResponse("message", "Could not load the pool")).build();
    }
    // Delete
    // session.delete(pool);
    poolManager.deletePool(pool);

    return Response.ok().build();
  }

}
