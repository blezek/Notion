/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.servlets;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.util.FileUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;

import java.io.File;
import java.io.InputStream;

/**
 * The Logger Level Servlet.
 * This servlet provides a browser-accessible
 * user interface for setting logger levels.
 */
public class LoggerLevelServlet extends Servlet {

	static final Logger logger = Logger.getLogger(LoggerLevelServlet.class);
	String home = "/";

	/**
	 * Construct a LoggerLevelServlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public LoggerLevelServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * The servlet method that responds to an HTTP GET.
	 * This method returns an HTML page containing a form for
	 * specifying a class and logger level.
	 * @param req the request object.
	 * @param res the response object.
	 */
	public void doGet(HttpRequest req, HttpResponse res) throws Exception {

		//Require that the user have the admin role
		if (!req.userHasRole("admin")) {
			res.setResponseCode(res.notfound);
			res.send();
			return;
		}

		if (req.hasParameter("suppress")) home = "";

		//Make the page and return it.
		Document xml = XmlUtil.getDocument();
		xml.appendChild( xml.createElement("LoggerLevel") );
		Document xsl = getDocument("LoggerLevelServlet.xsl");
		String[] params = new String[] { "home", home };
		res.write( XmlUtil.getTransformedText( xml, xsl, params ) );
		res.setContentType("html");
		res.disableCaching();
		res.send();
	}

	/**
	 * The servlet method that responds to an HTTP POST.
	 * This method interprets the posted parameters as a
	 * fully qualified class name and a logger level.
	 * It loads the class, sets the logger level, and
	 * then returns an HTML page containing a new form.
	 * @param req the request object.
	 * @param res the response object.
	 */
	public void doPost(HttpRequest req, HttpResponse res) throws Exception {

		//Make sure the user is authorized to do this.
		if (!req.userHasRole("admin") || !req.isReferredFrom(context)) {
			res.setResponseCode(res.notfound);
			res.send();
			return;
		}

		//Get the class and level
		String theClass = req.getParameter("class", "").trim();
		String theLevel = req.getParameter("level", "INFO");
		if (!theClass.equals("")) {
			Logger.getLogger(theClass).setLevel( Level.toLevel(theLevel) );
			logger.warn(theClass+" logger level set to "+theLevel);
		}

		//Make a new page and return it.
		doGet(req, res);
	}

	private Document getDocument(String name) throws Exception {
		File file = new File(root, name);
		InputStream in = FileUtil.getStream(file, "/"+name);
		return XmlUtil.getDocument(in);
	}

}











