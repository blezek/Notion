/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.servlets;

import org.apache.log4j.Logger;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.util.HtmlUtil;

import java.io.File;
import java.util.Arrays;
import java.util.Map;

/**
 * The EnvironmentServlet.
 */
public class EnvironmentServlet extends Servlet {

	static final Logger logger = Logger.getLogger(SysPropsServlet.class);
	private static final Runtime runtime = Runtime.getRuntime();
	private static long usedMemory() {
		return runtime.totalMemory() - runtime.freeMemory();
	}
	String home = "/";

	/**
	 * Construct an EnvironmentServlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public EnvironmentServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * The GET handler: return a page displaying the environment variables.
	 * @param req the request object
	 * @param res the response object
	 */
	public void doGet(HttpRequest req, HttpResponse res) {
		res.disableCaching();
		boolean admin = req.userHasRole("admin");
		if (req.hasParameter("suppress")) home = "";
		res.write(getPage(admin));
		res.setContentType("html");
		res.send();
	}

	//Create an HTML page containing the data.
	private String getPage(boolean admin) {
		String page =
				"<html>\n"
			+	" <head>\n"
			+	"  <title>Environment Variables</title>\n"
			+	"  <link rel=\"Stylesheet\" type=\"text/css\" media=\"all\" href=\"/BaseStyles.css\"></link>\n"
			+	"  <link rel=\"Stylesheet\" type=\"text/css\" media=\"all\" href=\"/JSPopup.css\"></link>\n"
			+	"  <style>\n"
			+	"   body {margin:0; padding:0;}\n"
			+	"   h1 {text-align:center;}\n"
			+	"   td {padding-left:5; padding-right:5; background:white; font-family:monospace;}\n"
			+	"  </style>\n"
			+	" </head>\n"
			+	" <body>\n"
			+	(!home.equals("") ? HtmlUtil.getCloseBox(home) : "")
			+ 	" <h1>Environment Variables</h1>\n"
			+	"  <center>\n"
			+	"   <table border=\"1\">\n"
			+	     display()
			+	"   </table>\n"
			+	"  </center>\n"
			+	" </body>\n"
			+	"</html>\n";
		return page;
	}

	//Return a String containing the HTML rows of a table
	//displaying all the environment variables.
	private String display() {
		String v;
		String sep = System.getProperty("path.separator",";");
		StringBuffer sb = new StringBuffer();

		Map<String,String> env = System.getenv();
		String[] n = new String[env.size()];
		n = env.keySet().toArray(n);
		Arrays.sort(n);
		for (int i=0; i<n.length; i++) {
			v = env.get(n[i]);

			//Make path and dirs variables more readable by
			//putting each element on a separate line.
			if (n[i].toLowerCase().contains("path"))
				v = v.replace(sep, sep+"<br/>");

			sb.append( "<tr><td>" + n[i] + "</td><td>" + v + "</td></tr>\n" );
		}
		return sb.toString();
	}
}
