/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.servlets;

import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.pipeline.Pipeline;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.ctp.stdstages.Scriptable;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.servlets.Servlet;
import org.rsna.util.FileUtil;
import org.rsna.util.HtmlUtil;

import java.io.File;
import java.util.List;

/**
 * The Filter Script Configurator servlet.
 * <p>
 * This servlet provides a browser-accessible user interface for
 * configuring the script file for a filter.
 * <p>
 * This servlet responds to both HTTP GET and POST.
 */
public class ScriptServlet extends Servlet {

	static final Logger logger = Logger.getLogger(ScriptServlet.class);
	String home = "/";

	/**
	 * Construct a ScriptServlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public ScriptServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * The servlet method that responds to an HTTP GET.
	 * <p>
	 * This method returns an HTML page containing a form for
	 * changing the contents of the script file.
	 * <p>
	 * The contents of the form are constructed
	 * from the text of the file.
	 * @param req The HttpServletRequest provided by the servlet container.
	 * @param res The HttpServletResponse provided by the servlet container.
	 */
	public void doGet(
			HttpRequest req,
			HttpResponse res) {

		//Make sure the user is authorized to do this.
		if (!req.userHasRole("admin")) {
			res.setResponseCode(res.forbidden);
			res.send();
			return;
		}

		if (req.hasParameter("suppress")) home = "";

		//Get the script file, if possible.
		int p,s,f;
		File scriptFile = null;
		try {
			p = Integer.parseInt(req.getParameter("p"));
			s = Integer.parseInt(req.getParameter("s"));
			f = Integer.parseInt(req.getParameter("f"));
			scriptFile = getScriptFile(p,s,f);
			//Now make either the page listing the various scriptable stages
			//or the page listing the scripts in the specified file.
			if (scriptFile != null)
				res.write(getScriptPage(p, s, f, scriptFile));
			else
				res.write(getListPage());
		}
		catch (Exception ex) { res.write(getListPage()); }

		//Return the page
		res.disableCaching();
		res.setContentType("html");
		res.send();
	}

	/**
	 * The servlet method that responds to an HTTP POST.
	 * <p>
	 * This method interprets the posted parameters as a new configuration
	 * for the script file and updates the file accordingly.
	 * It then returns an HTML page containing a new form constructed
	 * from the new contents of the file.
	 * <p>
	 * The contents of the form are constructed from the text
	 * of the file, not from a Properties object because
	 * all properties must be configurable, even those
	 * that are commented out in the properties file.
	 * @param req The HttpRequest provided by the servlet container.
	 * @param res The HttpResponse provided by the servlet container.
	 */
	public void doPost(
			HttpRequest req,
			HttpResponse res) {

		//Make sure the user is authorized to do this.
		if (!req.userHasRole("admin") || !req.isReferredFrom(context)) {
			res.setResponseCode(res.forbidden);
			res.send();
			return;
		}

		if (req.hasParameter("suppress")) home = "";

		//Get the parameters from the form.
		String script = req.getParameter("script");
		int p,s,f;
		File scriptFile = null;
		try {
			p = Integer.parseInt(req.getParameter("p"));
			s = Integer.parseInt(req.getParameter("s"));
			f = Integer.parseInt(req.getParameter("f"));
			scriptFile = getScriptFile(p,s,f);

			//Update the file if possible.
			if (scriptFile != null) {
				synchronized (this) { FileUtil.setText(scriptFile, script); }

				//Make a new page from the new data and send it out
				res.disableCaching();
				res.setContentType("html");
				res.write(getScriptPage(p, s, f, scriptFile));
				res.send();
				return;
			}
		}
		catch (Exception ex) { }
		res.setResponseCode(500); //Unable to perform the function.
		res.send();
	}

	//Get the script file, if possible
	private File getScriptFile(int p, int s, int f) {
		try {
			Configuration config = Configuration.getInstance();
			List<Pipeline> pipelines = config.getPipelines();
			Pipeline pipe = pipelines.get(p);
			List<PipelineStage> stages = pipe.getStages();
			PipelineStage stage = stages.get(s);
			if (stage instanceof Scriptable) {
				File[] scriptFiles = ((Scriptable)stage).getScriptFiles();
				return scriptFiles[f];
			}
		}
		catch (Exception ex) { }
		return null;
	}

	//Create an HTML page containing the list of script files.
	private String getListPage() {
		return responseHead("Select the Script File to Edit", "")
				+ makeList()
					+ responseTail();
	}

	private String makeList() {
		StringBuffer sb = new StringBuffer();
		Configuration config = Configuration.getInstance();
		List<Pipeline> pipelines = config.getPipelines();
		if (pipelines.size() != 0) {
			int count = 0;
			sb.append("<table border=\"1\" width=\"100%\">");
			for (int p=0; p<pipelines.size(); p++) {
				Pipeline pipe = pipelines.get(p);
				List<PipelineStage> stages = pipe.getStages();
				for (int s=0; s<stages.size(); s++) {
					PipelineStage stage = stages.get(s);
					if (stage instanceof Scriptable) {
						File[] scriptFiles = ((Scriptable)stage).getScriptFiles();
						for (int f=0; f<scriptFiles.length; f++) {
							File file = scriptFiles[f];
							if ((file != null) && file.exists()) {
								sb.append("<tr>");
								sb.append("<td class=\"list\">"+pipe.getPipelineName()+"</td>");
								sb.append("<td class=\"list\">"+stage.getName()+"</td>");
								sb.append("<td class=\"list\"><a href=\"/"+context
												+"?p="+p
												+"&s="+s
												+"&f="+f
												+(home.equals("") ? "&suppress" : "")
												+"\">"
												+file.getAbsolutePath()+"</a></td>");
								sb.append("</tr>");
								count++;
							}
						}
					}
				}
			}
			sb.append("</table>");
			if (count == 0) sb.append("<p>The configuration contains no editable scripts.</p>");
		}
		return sb.toString();
	}

	//Create an HTML page containing the form for configuring the file.
	private String getScriptPage(int p, int s, int f, File scriptFile) {
		return responseHead("Script Editor", scriptFile.getAbsolutePath())
				+ makeForm(p, s, f, scriptFile)
					+ responseTail();
	}

	private String makeForm(int p, int s, int f, File scriptFile) {
		String script = FileUtil.getText(scriptFile);

		StringBuffer form = new StringBuffer();
		form.append("<form method=\"POST\" accept-charset=\"UTF-8\" action=\"/"+context+"\">\n");
		form.append(hidden("p", p + ""));
		form.append(hidden("s", s + ""));
		form.append(hidden("f", f + ""));
		if (home.equals("")) form.append(hidden("suppress", ""));

		form.append("<center>\n");
		form.append("<textarea name=\"script\">" + script + "</textarea>\n");
		form.append("</center>");
		form.append("<br/>\n");
		form.append("<input class=\"button\" type=\"submit\" value=\"Update the script file\"/>\n");
		form.append("</form>\n");
		return form.toString();
	}

	private String hidden(String name, String text) {
		return "<input type=\"hidden\" name=\"" + name + "\" value=\"" + text + "\"/>";
	}

	private String textInput(String name, String value) {
		return "<input name=\"" + name + "\" value='" + value + "'/>";
	}

	private String responseHead(String title, String subtitle) {
		String head =
				"<html>\n"
			+	" <head>\n"
			+	"  <title>"+title+"</title>\n"
			+	"  <link rel=\"Stylesheet\" type=\"text/css\" media=\"all\" href=\"/BaseStyles.css\"></link>\n"
			+	"   <style>\n"
			+	"    body {margin-top:0; margin-right:0;}\n"
			+	"    h1 {text-align:center; margin-top:10;}\n"
			+	"    h2 {text-align:center; font-size:12pt; margin:0; margin-bottom:10px; padding:0; font-weight:normal; font-family: Arial, Helvetica, Verdana, sans-serif;}\n"
			+	"    textarea {width:75%; height:500px;}\n"
			+	"    td.list {background:white}\n"
			+	"    .button {width:250}\n"
			+	"   </style>\n"
			+	" </head>\n"
			+	" <body>\n"
			+	(home.equals("") ? "" : HtmlUtil.getCloseBox(home))
			+	"  <h1>"+title+"</h1>\n"
			+	(subtitle.equals("") ? "" : "  <h2>"+subtitle+"</h2>")
			+	"  <center>\n";
		return head;
	}

	private String responseTail() {
		String tail =
				"  </center>\n"
			+	" </body>\n"
			+	"</html>\n";
		return tail;
	}

}
