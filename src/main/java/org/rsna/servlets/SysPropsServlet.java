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
import java.util.Enumeration;
import java.util.Formatter;
import java.util.Properties;

/**
 * The SysPropsServlet.
 */
public class SysPropsServlet extends Servlet {

	static final Logger logger = Logger.getLogger(SysPropsServlet.class);
	private static final Runtime runtime = Runtime.getRuntime();
	private static long usedMemory() {
		return runtime.totalMemory() - runtime.freeMemory();
	}
	String home = "/";

	/**
	 * Construct a SysPropsServlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public SysPropsServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * The GET handler: return a page displaying the system properties.
	 * @param req the request object
	 * @param res the response object
	 */
	public void doGet(HttpRequest req, HttpResponse res) {
		res.disableCaching();
		boolean admin = req.userHasRole("admin");
		if (admin && (req.getParameter("gc") != null)) collect();
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
			+	"  <title>System Properties</title>\n"
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
			+ 	" <h1>System Properties</h1>\n"
			+	"  <center>\n"
			+	"   <table border=\"1\">\n"
			+	     displayProperties(admin)
			+	"   </table>\n"
			+	"  </center>\n"
			+	" </body>\n"
			+	"</html>\n";
		return page;
	}

	//Return a String containing an HTML table row containing
	//the current memory in use, with a link allowing garbage
	//collection if the user has admin privileges.
	private void displayMemory(StringBuffer sb, boolean admin) {
		Formatter formatter = new Formatter(sb);
		sb.append( "<tr><td>MEMORY IN USE</td><td>" );
		formatter.format("%,d", usedMemory());
		if (admin) {
			sb.append(" (<a href=\"?gc");
			if (home.equals("")) sb.append("&suppress");
			sb.append("\">collect garbage</a>)");
		}
		sb.append("\n");
		sb.append( "</td></tr>\n" );
		sb.append( "<tr><td>TOTAL MEMORY</td><td>"  );
		formatter.format("%,d", runtime.totalMemory());
		sb.append( "</td></tr>" );
	}

	//Return a String containing the HTML rows of a table
	//displaying all the Java System properties.
	private String displayProperties(boolean admin) {
		String v;
		String sep = System.getProperty("path.separator",";");
		StringBuffer sb = new StringBuffer();
		displayMemory(sb, admin);

		Properties p = System.getProperties();
		String[] n = new String[p.size()];
		Enumeration e = p.propertyNames();
		for (int i=0; i< n.length; i++) n[i] = (String)e.nextElement();
		Arrays.sort(n);
		for (int i=0; i<n.length; i++) {
			v = p.getProperty(n[i]);

			//Make path and dirs properties more readable by
			//putting each element on a separate line.
			if (n[i].endsWith(".path") ||
				n[i].endsWith(".dirs"))
					v = v.replace(sep, sep+"<br/>");

			//Make definition, access, and loader properties more
			//readable by putting each element on a separate line.
			if (n[i].endsWith(".definition") ||
				n[i].endsWith(".access") ||
				n[i].endsWith(".loader"))
					v = v.replace(",", ",<br>");

			sb.append( "<tr><td>" + n[i] + "</td><td>" + v + "</td></tr>\n" );
		}
		return sb.toString();
	}

	//Collect the garbage.

	private static void collect() {
		int i;
		long usedMemory1 = usedMemory();
		long usedMemory2 = Long.MAX_VALUE;
		for (i=0; (usedMemory1 < usedMemory2) && (i < 40); i++) {
			runtime.runFinalization();
			runtime.gc();
			Thread.currentThread().yield();
			usedMemory2 = usedMemory1;
			usedMemory1 = usedMemory();
		}
	}
}
