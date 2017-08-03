package edu.mayo.qia.pacs.managed;

import org.h2.tools.Server;

import io.dropwizard.lifecycle.Managed;

public class DBWebServer implements Managed {
  String port;
  Server server;

  public DBWebServer(String port) {
    this.port = port;
  }

  @Override
  public void start() throws Exception {
    server = Server.createWebServer("-webPort", port).start();

  }

  @Override
  public void stop() throws Exception {
    server.stop();
  }

}
