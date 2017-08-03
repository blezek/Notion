package edu.mayo.qia.pacs.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;

import com.sun.jersey.api.client.ClientResponse;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.net.URI;

import edu.mayo.qia.pacs.components.Device;
import edu.mayo.qia.pacs.components.Pool;

@RunWith(SpringJUnit4ClassRunner.class)
public class DeviceTest extends PACSTest {

  @Test
  public void createDevice() {
    // CURL Code
    /*
     * curl -X POST -H "Content-Type: application/json" -d
     * '{"name":"foo","path":"bar"}' http://localhost:11118/pool
     */
    ClientResponse response = null;
    URI uri;
    uri = UriBuilder.fromUri(baseUri).path("/pool").build();
    Pool pool = new Pool("deviceTest", "Test out device creation", "foo", false);
    response = client.resource(uri).type(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, pool);
    assertEquals("Got result", 200, response.getStatus());
    pool = response.getEntity(Pool.class);
    logger.info("Entity back: " + pool);
    assertTrue("Assigned an id", pool.poolKey != 0);

    // Create a device
    Device device = new Device();
    device.applicationEntityTitle = "test";
    device.hostName = "localhost";
    device.port = 1234;

    uri = UriBuilder.fromUri(baseUri).path("/pool/" + pool.poolKey + "/device").build();
    response = client.resource(uri).type(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, device);
    assertEquals("Got result", 200, response.getStatus());
    device = response.getEntity(Device.class);
    logger.info("Entity back: " + device);
    assertTrue("Assigned a deviceKey", device.deviceKey != 0);

  }

}
