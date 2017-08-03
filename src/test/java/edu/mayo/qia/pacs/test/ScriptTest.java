package edu.mayo.qia.pacs.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.jersey.api.client.ClientResponse;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.ScriptableObject;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.net.URI;
import java.util.UUID;

import edu.mayo.qia.pacs.components.Pool;
import edu.mayo.qia.pacs.components.Script;

@RunWith(SpringJUnit4ClassRunner.class)
public class ScriptTest extends PACSTest {

  @Test
  public void simplePrint() throws Exception {
    ScriptEngineManager manager = new ScriptEngineManager();
    ScriptEngine engine = manager.getEngineByName("JavaScript");
    Bindings bindings = engine.createBindings();
    bindings.put("num", "20");
    Object result = null;
    // result =
    // engine.eval("print ( fib(num) ); function fib(n) {  if(n <= 1) return n;  return fib(n-1) + fib(n-2); };",
    // bindings);
    result = engine.eval("function foo () { return 'hi' }; foo();", bindings);
    if (result instanceof String) {
      logger.info("\n======\n" + result + "\n=======");
      assertEquals("Result from javascript", "hi", result.toString());
    } else {
      fail("Did not get a string back");
    }

    // Last statement ends up being the result value from an eval call
    result = engine.eval("function foo () { return 'hi' }; 'hi';", bindings);
    if (result instanceof String) {
      logger.info("\n======\n" + result + "\n=======");
      assertEquals("Result from javascript", "hi", result.toString());
    } else {
      fail("Did not get a string back");
    }

  }

  @Test
  public void defaultScripts() throws Exception {
    UUID uid = UUID.randomUUID();
    String aet = uid.toString().substring(0, 10);
    Pool pool = new Pool(aet, aet, aet, false);
    pool = createPool(pool);

    ClientResponse response = null;
    URI uri;
    uri = UriBuilder.fromUri(baseUri).path("/pool/").path("" + pool.poolKey).path("/script").build();
    response = client.resource(uri).type(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).get(ClientResponse.class);
    ObjectNode json = response.getEntity(ObjectNode.class);
    assertTrue("Result", json.has("script"));
    assertTrue("Has Script", json.has("script"));
    assertFalse("Has Script content", json.get("script").asText().isEmpty());
  }

  @Test
  public void rhino() {
    Context context = Context.enter();
    try {
      ScriptableObject scope = context.initStandardObjects();
      NativeObject obj = new NativeObject();
      obj.defineProperty("test", "value", NativeObject.READONLY);
      ScriptableObject.putProperty(scope, "obj", obj);
      Object result = context.evaluateString(scope, "obj.test", "inline", 1, null);
      logger.info("\n=====\n" + result + "\n======");
      assertTrue("String back", result instanceof String);
      assertTrue("String value", result.toString().equals("value"));

      result = context.evaluateString(scope, "'garf'", "inline", 1, null);
      logger.info("\n=====\n" + result + "\n======");
      assertTrue("String back", result instanceof String);
      assertTrue("String value", result.toString().equals("garf"));

    } finally {
      Context.exit();
    }
  }

  @Test
  public void createScript() {
    // CURL Code
    /* curl -X POST -H "Content-Type: application/json" -d
     * '{"name":"foo","path":"bar"}' http://localhost:11118/pool */
    ClientResponse response = null;
    UUID uid = UUID.randomUUID();
    String aet = uid.toString().substring(0, 10);

    Pool pool = createPool(new Pool(aet, aet, aet, false));
    Script script = createScript(new Script(pool, "foo"));

    // Query it back
    URI uri;
    uri = UriBuilder.fromUri(baseUri).path("/pool").path(Integer.toString(pool.poolKey)).path("script/").build();
    response = client.resource(uri).type(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).get(ClientResponse.class);
    assertEquals("Got result", Status.OK.getStatusCode(), response.getStatus());
    Script serverScript = response.getEntity(Script.class);
    logger.info("Entity back: " + serverScript);
    assertEquals("Script is the same", script.script, serverScript.script);

  }

}
