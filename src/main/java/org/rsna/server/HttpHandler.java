/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.server;

import org.apache.log4j.Logger;
import org.rsna.servlets.Servlet;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;

/**
 * The thread that handles an individual HTTP connection.
 */
public class HttpHandler extends Thread {

	static final Logger logger = Logger.getLogger(HttpHandler.class);

	Socket socket;
	ServletSelector selector;

	/**
	 * Construct an HttpHandler.
	 * @param socket the socket on which the connection was received.
	 */
	public HttpHandler(Socket socket, ServletSelector selector) {
		super("HttpHandler");
		this.socket = socket;
		this.selector = selector;
	}

	/**
	 * Handle the connection in a separate Thread, getting the streams,
	 * selecting a Servlet to handle the request, and returning the response.
	 */
	@Override
  public void run() {
		HttpResponse res = null;
		HttpRequest req = null;
		try {
			//Make a response
			res = new HttpResponse(socket);

			//Get the request
			req = new HttpRequest(socket);

			//Get the Servlet
			Servlet servlet = selector.getServlet(req);

			//Call the appropriate method
			if (req.method.equals("GET")) {
				servlet.doGet(req,res);
			}
			else if (req.method.equals("POST")) {
				servlet.doPost(req,res);
			}
			else {
				res.setResponseCode(HttpResponse.notimplemented);
				res.send();
				logger.debug("Unimplemented request ("+req.method+") received from "+req.getRemoteAddress());
			}
		}
		catch (Exception ex) {
			if (req != null) {
				logger.error("Internal server error ("+req.toString()+")",ex);
				try {
					StringWriter sw = new StringWriter();
					ex.printStackTrace(new PrintWriter(sw));
					res = new HttpResponse(socket);
					res.setResponseCode(HttpResponse.ok); //so the browser will display the page
					res.write("<html>");
					res.write("<head><title>ERROR</title></head>");
					res.write("<body><h1>Internal Server Error (HTTP 500)</h1><pre>"+sw.toString()+"</pre></body>");
					res.write("</html>");
					res.send();
				}
				catch (Exception ignore) { /*Don't log this; the real problem has been logged above.*/ }
			}
			else {
				logger.error("Internal server error (req==null)",ex);
			}
		}
		//Close everything.
		if (req != null) req.close();
		if (res != null) res.close();
		try { socket.close(); }
		catch (Exception ex) { logger.debug("Unable to close the socket."); }
	}

}
