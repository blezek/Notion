/*---------------------------------------------------------------
*  Copyright 2009 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.service;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocketFactory;

import org.apache.log4j.Logger;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;

import java.net.ServerSocket;
import java.net.Socket;

/**
 * A Thread that implements a single HTTP Service.
 */
public class HttpService extends Thread {

	static final Logger logger = Logger.getLogger(HttpService.class);

	ServerSocket serverSocket;
	boolean ssl;
	int port;
	Service service;
	String name;

    public HttpService(boolean ssl, int port, Service service) throws Exception {
		this(ssl, port, service, null);
	}

    public HttpService(boolean ssl, int port, Service service, String name) throws Exception {
		super("HttpService");
		this.ssl = ssl;
		this.port = port;
		this.name = name;
		this.service = service;
		ServerSocketFactory serverSocketFactory =
			ssl ? SSLServerSocketFactory.getDefault() : ServerSocketFactory.getDefault();
		serverSocket = serverSocketFactory.createServerSocket(port);//Use the default backlog
	}

	// Start the HttpService and accept connections.
	@Override
  public void run() {
		logger.info("HttpService open on port "+port + ((name!=null)?" ("+name+")":"") );
		while (!this.isInterrupted()) {
			try {
				//Wait for a connection
				Socket socket = serverSocket.accept();

				//Handle the connection in a separate thread
				if (!socket.isClosed()) {
					Handler handler = new Handler(socket);
					handler.start();
				}
			}
			catch (Exception ex) { break; }
		}
		try { serverSocket.close(); }
		catch (Exception ex) { logger.warn("Unable to close the server socket."); }
		serverSocket = null;
	}

	// Stop the HttpReceiver.
	public void stopServer() {
		this.interrupt();
	}

	//Class to handle one connection and service the request.
	class Handler extends Thread {

		Socket socket;

		public Handler(Socket socket) {
			this.socket = socket;
		}

		@Override
    public void run() {
			HttpResponse res = null;
			HttpRequest req = null;
			try {
				//Make a response
				res = new HttpResponse(socket);

				//Get the request
				req = new HttpRequest(socket);

				//Service it
				service.process(req, res);
			}
			catch (Exception ex) {
				if (ex instanceof javax.net.ssl.SSLException) {
					logger.warn("SSL connection error, plaintext?");
				}
				else {
					logger.error("Internal server error.", ex);
					try {
						res = new HttpResponse(socket);
						res.setResponseCode(500); //internal server error
						res.send();
					}
					catch (Exception ignore) { /*Don't log; the real error is logged above.*/ }
				}
			}

			//Close everything.
			if (req != null) req.close();
			if (res != null) res.close();
			try { socket.close(); }
			catch (Exception ex) { logger.warn("Unable to close the socket."); }
		}
	}
}

