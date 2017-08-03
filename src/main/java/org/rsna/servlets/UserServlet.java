/*---------------------------------------------------------------
*  Copyright 2013 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.servlets;

import org.apache.log4j.Logger;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.server.User;
import org.rsna.server.Users;
import org.rsna.server.UsersXmlFileImpl;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;

/**
 * The UserServlet. This servlet is intended for use by Ajax
 * calls on web pages which need to know whether the user is logged in
 * and what roles he has.
 */
public class UserServlet extends Servlet {

	static final Logger logger = Logger.getLogger(UserServlet.class);

	/**
	 * Construct a UserServlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public UserServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * The GET handler: return an XML structure containing:
	 * <ul>
	 * <li>user's name (username)
	 * <li>user's location (local or remote)
	 * <li>client IP address
	 * <li>server's Users class ("xml" or "")
	 * <li>user's roles (multiple role elements)
 	 * </ul>
	 * @param req the request object
	 * @param res the response object
	 */
	@Override
  public void doGet(HttpRequest req, HttpResponse res) {
		if (logger.isDebugEnabled()) {
			logger.info(req.toString()
						+"\nHeaders:\n"+req.listHeaders("      ")
						+"Cookies:\n"+req.listCookies("      "));
		}
		String name = "";
		String[] roles = new String[0];
		User user = req.getUser();
		if (user != null) {
			name = user.getUsername();
			roles = user.getRoleNames();
		}
		Users users = Users.getInstance();
		boolean usersImplIsXML = (users instanceof UsersXmlFileImpl);
		boolean local = req.isFromLocalHost();
		String ip = req.getRemoteAddress();
		try {
			Document doc = XmlUtil.getDocument();
			Element root = doc.createElement("user");
			root.setAttribute("ip", ip);
			root.setAttribute("name", name);
			root.setAttribute("location", (local ? "local" : "remote"));
			root.setAttribute("usersImpl", (usersImplIsXML ? "xml" : ""));
			for (String role : roles) {
				role = role.trim();
				if (!role.equals("")) {
					Element r = doc.createElement("role");
					r.setTextContent(role);
					root.appendChild(r);
				}
			}
			doc.appendChild(root);
			res.write(XmlUtil.toPrettyString(doc));
		}
		catch (Exception ex) { res.write("<user/>"); }
		res.setContentType("xml");
		res.disableCaching();
		res.send();
	}

}
