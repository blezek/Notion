package edu.mayo.qia.pacs.rest;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import edu.mayo.qia.pacs.Notion;
import edu.mayo.qia.pacs.components.PoolManager;

@Path("/metrics")
@Component
@Scope("singleton")
public class MetricsEndpoint {

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  PoolManager poolManager;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response getMetrics() {
    JsonNode v = objectMapper.valueToTree(Notion.metrics);
    if (v instanceof ObjectNode) {
      ObjectNode json = (ObjectNode) v;
      ArrayNode a = json.putArray("pools");
      for (String pool : PoolManager.getPoolContainers().keySet()) {
        a.add(pool);
      }
    }
    return Response.ok(v).build();
  }
}
