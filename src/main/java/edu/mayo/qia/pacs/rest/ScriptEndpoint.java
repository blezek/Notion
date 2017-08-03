package edu.mayo.qia.pacs.rest;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.sun.jersey.spi.resource.PerRequest;

import org.apache.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import edu.mayo.qia.pacs.components.Pool;
import edu.mayo.qia.pacs.components.PoolContainer;
import edu.mayo.qia.pacs.components.PoolManager;
import edu.mayo.qia.pacs.components.Script;
import edu.mayo.qia.pacs.ctp.Anonymizer;
import io.dropwizard.hibernate.UnitOfWork;

@Scope("prototype")
@Component
@PerRequest
public class ScriptEndpoint {

  static Logger logger = Logger.getLogger(ScriptEndpoint.class);

  @Autowired
  SessionFactory sessionFactory;

  @Autowired
  JdbcTemplate template;

  @Autowired
  PoolManager poolManager;

  public int poolKey;

  /** List all the Scripts. */
  @GET
  @UnitOfWork
  @Produces(MediaType.APPLICATION_JSON)
  public Response listScripts() {
    // Look up the pool and change it
    Session session = sessionFactory.getCurrentSession();
    Pool pool = (Pool) session.byId(Pool.class).load(poolKey);
    // Force load
    if (pool.getScript() == null) {
      pool.script = new Script(Script.createDefaultScript());
      pool.script.setPool(pool);
      session.save(pool);
    }
    return Response.ok(pool.getScript()).build();
  }

  /** Try a Script. */
  @PUT
  @Path("/try")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response tryScript(Script script) {
    PoolContainer container = poolManager.getContainer(poolKey);
    return Response.ok(new SimpleResponse("result", Anonymizer.tryScript(container, script))).build();
  }

  /** Update a Script. */
  @PUT
  @Path("/")
  @UnitOfWork
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response modifyScript(Script inScript) {
    // Look up the pool and change it
    Session session = sessionFactory.getCurrentSession();
    Pool pool = (Pool) session.byId(Pool.class).load(poolKey);
    pool.getScript().update(inScript);
    // session.save(pool);
    session.save(pool.getScript());
    return Response.ok(pool.getScript()).build();
  }

}
