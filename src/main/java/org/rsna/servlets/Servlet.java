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
import org.rsna.util.Cache;
import org.rsna.util.FileUtil;
import org.rsna.util.StringUtil;

import java.io.File;
import java.net.URL;

/**
 * The base class for servlets.
 */
public class Servlet {

	static final Logger logger = Logger.getLogger(Servlet.class);

	/** The base directory for serving files. */
	public File root;

	/** The path by which the servlet is identified in a URL. */
	public String context;

	/**
	 * Static init method to set up the index.html file if necessary.
	 * If the index.html file does NOT exist in the root of the servlet
	 * AND if the example-index.html file DOES exist, this method copies
	 * the example to the real index.html file; otherwise, it leaves
	 * everything alone.
	 */
	public static void init(File root, String context) {
		File contextRoot = new File(root, context);
		File index = new File(contextRoot,"index.html");
		if (!index.exists()) {
			File exindex = new File(contextRoot,"example-index.html");
			if (exindex.exists()) FileUtil.copy(exindex, index);
		}
	}

	/**
	 * Construct a Servlet. Since this is the base class for all servlets
	 * and it is the default servlet, the context is typically the empty
	 * string, indicating that it matches any context that has not already
	 * been matched in the ServletSelector by a Servlet subclass.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public Servlet(File root, String context) {
		this.root = root;
		this.context = context;
	}

	/**
	 * The method called by the server when it shuts down.
	 * Servlets which have data to be saved or committed to
	 * a database should override this method.
	 */
	public void destroy() { }

	/**
	 * The default handler for GET requests.
	 * Return the requested file in the response. This method looks
	 * first for the file in the path from the root. If it does not
	 * find it there, it tries to load it as a resource on the classpath.
	 * If that fails, it returns a NOT FOUND response code.
	 * @param req the request object
	 * @param res the response object
	 */
	public void doGet(HttpRequest req, HttpResponse res) throws Exception {
		URL url = null;
		File file = getRequestedFile(req);
		String ct = res.setContentType(file);
		if ((ct == null) || ct.startsWith("application/")) res.disableCaching();

		if (file.exists()) {
			//The file exists in the root directory tree.
			//Either serve it or return a notmodified code.
			long clientLMDate = req.getConditionalTime();
			long fileLMDate = file.lastModified();
			if ((clientLMDate > 0) && (fileLMDate <= clientLMDate)) {
				res.setResponseCode( HttpResponse.notmodified );
				res.setETag(fileLMDate);
			}
			else {
				res.write(file);
				res.setLastModified(fileLMDate);
				res.setETag(fileLMDate);
			}
		}
		else {
			//The file does not exist in the root directory tree.
			//If there is a cache. try to get the file from there.
			Cache cache = Cache.getInstance();
			if (cache != null) {
				String p = req.path;
				if (p.startsWith("/")) p = p.substring(1);
				file = cache.getFile(p);
				if (file != null) res.write(file);
				else res.setResponseCode( HttpResponse.notfound );
			}
			else {
				//There is no cache, see if the file can
				//be obtained from the classpath.
				if ((url=getClass().getResource(req.path)) != null)  res.write(url);
				else res.setResponseCode( HttpResponse.notfound );
			}
		}
		res.send();
	}

	/**
	 * The default handler for POST requests.
	 * Return a not found error in the response.
	 * @param req the request object
	 * @param res the response object
	 */
	public void doPost(HttpRequest req, HttpResponse res) throws Exception {
		res.disableCaching();
		res.setResponseCode( HttpResponse.notfound );
		res.send();
	}

	/**
	 * Get the file identified in an HttpRequest.
	 * @param req the request object
	 * @return the file identified by the request, treating
	 * the path as an offset to the ROOT directory.
	 */
	public File getRequestedFile(HttpRequest req) {
		String p = req.path;
		if (p.startsWith("/")) p = p.substring(1);
		File file = new File(root, p);
		if (file.exists() && file.isDirectory()) {
			file = new File(file, "index.html");
			if (!file.exists()) {
				File parent = file.getParentFile();
				file = new File(parent, "index.htm");
			}
		}
		return file;
	}

	/**
	 * Filter a string for cross-site scripting attacks.
	 */
	public String filter(String s) {
		return StringUtil.filterXSS(s).replaceAll("<[^>]*>","").replaceAll("[<%>]","");
	}

}
