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
import org.rsna.server.Path;
import org.rsna.util.XmlUtil;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;

/**
 * The Web Start Application Server servlet.
 */
public class ApplicationServer extends Servlet {

	static final Logger logger = Logger.getLogger(ApplicationServer.class);

	/**
	 * Construct an ApplicationServer servlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public ApplicationServer(File root, String context) {
		super(root, context);
	}

	/**
	 * The servlet method that responds to an HTTP GET.
	 *
	 * The servlet responds to the path <tt>/webstart/{appname}?{params}</tt>
	 *
	 * Parameters must be single-valued.
	 *
	 * All the files for the app must be in the ROOT/{appname} directory.
	 *
	 * There must be a file named {appname}.xsl in the directory to produce
	 * the jnlp file. The XSL program must process an XSL document in this form:
	 * <pre>
	 * {@code
	 * <jnlp>
	 *     <environment>
	 *         <protocol>http[s]</protocol>
	 *         <host>{ip:port}</host>
	 *         <application>{appname}</application>
	 *     </environment>
	 *     <params>
	 *         <{param1name}><![CDATA[{param1value}]]></{param1name}>
	 *         etc.
	 *     </params>
	 * </jnlp>
	 * }
	 * </pre>
	 */
	@Override
  public void doGet(HttpRequest req, HttpResponse res) throws Exception {

		logger.debug("Webstart request:\n"+req.toString());

		Path path = req.getParsedPath();
		String protocol = req.getProtocol();
		String host = req.getHost();

		//See if this request points to the XSL file for launching an application
		if (path.length() > 1) {
			String appname = path.element(1);
			File dir = new File(root, appname);
			File xslFile = new File(dir, appname+".xsl");
			if (xslFile.exists()) {
				try {
					//Create the document for transformation into the jnlp file.
					Document doc = XmlUtil.getDocument();
					Element root = doc.createElement("jnlp");
					doc.appendChild(root);

					Element environment = doc.createElement("environment");
					environment.appendChild(getTextElement(doc, "protocol", protocol));
					environment.appendChild(getTextElement(doc, "host", host));
					environment.appendChild(getTextElement(doc, "application", appname));
					root.appendChild(environment);

					String[] paramNames = req.getParameterNames();
					Element params = doc.createElement("params");
					for (String paramName : paramNames) {
						params.appendChild(getParamElement(doc, paramName, req.getParameter(paramName)));
					}
					root.appendChild(params);

					//Do the transform and get the jnlp document.
					logger.debug("Application: "+appname+" params document\n"+XmlUtil.toPrettyString(doc));
					Document jnlp = XmlUtil.getTransformedDocument(doc, xslFile, null);
					logger.debug("Application: "+appname+" JNLP document\n"+XmlUtil.toPrettyString(jnlp));

					//Send the jnlp
					res.setContentType("jnlp");
					res.write(XmlUtil.toString(jnlp));
					res.send();
					return;
				}
				catch (Exception unable) { }
			}
		}
		res.setResponseCode(HttpResponse.notfound);
		res.send();
	}

	private Element getTextElement(Document doc, String name, String value) {
		Element e = doc.createElement(name);
		e.setTextContent(value);
		return e;
	}

	private Element getParamElement(Document doc, String name, String value) {
		Element e = doc.createElement("param");
		CDATASection cdata = doc.createCDATASection(name.trim() + "=" + value);
		e.appendChild(cdata);
		return e;
	}

}