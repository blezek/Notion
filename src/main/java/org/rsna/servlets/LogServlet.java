/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.servlets;

import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.util.FileUtil;
import org.rsna.util.HtmlUtil;
import org.rsna.util.StringUtil;

import java.io.File;

/**
 * The LogServlet.
 * This servlet provides a browser-accessible user interface for
 * viewing the log files in the server's logs directory.
 */
public class LogServlet extends Servlet {

	String home = "/";

	/**
	 * Construct a LogServlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public LogServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * The servlet method that responds to an HTTP GET.
	 * If called with no file path, this method returns an
	 * HTML page listing the files in the logs directory in reverse
	 * chronological order. Each filename is a link to display the file's
	 * contents. If called with a file path, this method returns
	 * the contents of the file in an HTML page.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doGet( HttpRequest req, HttpResponse res ) {
		res.disableCaching();

		//Require that the user have the admin role
		if (!req.userHasRole("admin")) {
			res.setResponseCode(res.notfound);
			res.send();
			return;
		}

		//Get the logs directory
		File dir = new File("logs");

		//Get the filename, if present
		String path = req.path;
		if (path.startsWith("/")) path = path.substring(1);
		String[] pathElements = path.split("/");
		String filename = null;
		if (pathElements.length > 1) filename = pathElements[1];

		//Get the page
		if (req.hasParameter("suppress")) home = "";
		String page;
		if ((filename == null) || filename.trim().equals("") || filename.trim().equals("/"))
			page = getDirectoryPage(dir, context, home);
		else
			page = getFilePage(new File(dir, filename.substring(filename.lastIndexOf("/")+1)));

		//Send it out
		res.disableCaching();
		res.setContentType("html");
		res.write(page);
		res.send();
	}

	//Make a page listing the files in the directory, sorted by last modified date.
	private String getDirectoryPage(File dir, String contextPath, String home) {
		File[] files = FileUtil.listSortedFiles(dir);

		//Make the page
		StringBuffer page = new StringBuffer(responseHead(home));
		if (files.length != 1) {
			page.append("<div class=\"logdir\">");
			page.append("<table>");
			for (int i=files.length-1; i>=0; i--) {
				if (files[i].length() != 0) {
					page.append(
						tr(
							td(a(contextPath+"/"+files[i].getName(), "logtext", files[i].getName()))
								+
							td(StringUtil.getDateTime(files[i].lastModified(),"&nbsp;&nbsp;&nbsp;"))
								+
							td(
								"style=\"text-align:right\"",
								StringUtil.insertCommas(Long.toString(files[i].length())))
						)
					);
				}
			}
			page.append("</table>");
			page.append("</div>");
			page.append("<iframe name=\"logtext\" id=\"logtext\">-</iframe>");
		}
		else page.append("<iframe name=\"logtext\" id=\"logtext\" src=\""+contextPath+"/"+files[0].getName()+"\">-</iframe>");
		page.append(responseTail());
		return page.toString();
	}

	//Make a page displaying the contents of a file.
	private String getFilePage(File file) {
		return "<pre>" + StringUtil.displayable(FileUtil.getText(file)) + "</pre>";
	}

	private String responseHead(String home) {
		String head =
				"<html>\n"
			+	" <head>\n"
			+	"  <title>LogViewer</title>\n"
			+	"  <link rel=\"Stylesheet\" type=\"text/css\" media=\"all\" href=\"/BaseStyles.css\"></link>\n"
			+	"   <style>\n"
			+	"    body {margin:0; padding:0;}\n"
			+	"    iframe {height:100; width:100%; background:white;}\n"
			+	"    td {text-align:left; background:white; padding:5; padding-right:20;}\n"
			+	"    .logdir {height:150; background:white; overflow:auto}\n"
			+	"    h1 {text-align:center; margin-top:5; margin-bottom:5;}\n"
			+	"   </style>\n"
			+	script()
			+	" </head>\n"
			+	" <body scroll=\"no\">\n"
			+	(!home.equals("") ? HtmlUtil.getCloseBox(home) : "")
			+	"   <h1>Log Viewer</h1>\n";
		return head;
	}

	private String responseTail() {
		String tail =
				" </body>\n"
			+	"</html>\n";
		return tail;
	}

	private String script() {
		return
				"<script>\n"
			+	"function adjustHeight() {\n"
			+	"	var logtext = document.getElementById('logtext');\n"
			+	"	var h = getHeight() - logtext.offsetTop;\n"
			+	"	if (h < 50) h = 50;\n"
			+	"	logtext.style.height = h;\n"
			+	"}\n"
			+	"function getHeight() {\n"
			+	"	if (document.all) return document.body.clientHeight;\n"
			+	"	return window.innerHeight - 22;\n"
			+	"}\n"
			+	"onload = adjustHeight;\n"
			+	"onresize = adjustHeight;\n"
			+	"</script>\n";
	}

	private static String tr(String text) {
		return "<tr>" + text + "</tr>";
	}

	private static String td(String text) {
		return "<td>" + text + "</td>";
	}

	private static String td(String attributes, String text) {
		return "<td" + (!attributes.equals("")? " "+attributes : "") + ">" + text+ "</td>";
	}

	private static String a(String href, String target, String text) {
		return "<a href=\"" + href + "\""
				+ (!target.equals("")? " target=\"" + target + "\"" : "") + "\">"
				+ text
				+ "</a>";
	}

}











