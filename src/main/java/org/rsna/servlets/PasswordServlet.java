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
import org.rsna.server.User;
import org.rsna.server.Users;
import org.rsna.server.UsersXmlFileImpl;
import org.rsna.util.FileUtil;
import org.rsna.util.XmlUtil;

import java.io.File;

/**
 * The PasswordServlet.
 */
public class PasswordServlet extends Servlet {

	static final Logger logger = Logger.getLogger(PasswordServlet.class);

	/**
	 * Construct a PasswordServlet.
	 * @param root the root directory of the server.
	 * @param context the path identifying the servlet.
	 */
	public PasswordServlet(File root, String context) {
		super(root, context);
	}

	/**
	 * The GET handler: display a page containing a form allowing
	 * the user to change his password. The page is not actually
	 * stored in the root directory of the servlet. It is instead
	 * stored in the root directory of the program. This makes the
	 * file available for all server instances, even ones on other ports.
	 * @param req the request object
	 * @param res the response object
	 */
	@Override
  public void doGet(HttpRequest req, HttpResponse res) {
		//Get a password page from one of three places:
		//-- the pages directory
		//-- the root directory
		//-- the jar file
		//Supply the first one found.
		String passwordPage = "";
		File passwordFilePages = new File("pages/password.html");
		File passwordFileRoot = new File(root, "password.html");

		if (passwordFilePages.exists()) {
			passwordPage = FileUtil.getText(passwordFilePages);
		}
		else if (passwordFileRoot.exists()) {
			passwordPage = FileUtil.getText(passwordFileRoot);
		}
		else {
			passwordPage = FileUtil.getText( getClass().getResourceAsStream("/password.html") );
		}

		res.write(passwordPage);
		res.disableCaching();
		res.setContentType("html");
		res.send();
	}

	/**
	 * The POST handler: change the user's password
	 * if both supplied passwords match.
	 * @param req the request object
	 * @param res the response object
	 */
	@Override
  public void doPost(HttpRequest req, HttpResponse res) {
		if (req.isReferredFrom(context)) {
			String pw1 = req.getParameter("pw1", "").trim();
			String pw2 = req.getParameter("pw2", "").trim();
			if (pw1.equals(pw2) && !pw1.equals("")) {
				Users users = Users.getInstance();
				User user = req.getUser();
				if (user != null) {
					user.setPassword(pw1.trim());
					if (users instanceof UsersXmlFileImpl) {
						UsersXmlFileImpl uxml = (UsersXmlFileImpl)users;
						uxml.addUser(user);
						File usersXmlFile = new File("users.xml");
						String usersString = XmlUtil.toString(uxml.getXML());
						FileUtil.setText(usersXmlFile, usersString);
					}
					res.redirect("/");
					return;
				}
			}
		}
		res.setResponseCode(HttpResponse.forbidden);
		res.send();
	}
}
