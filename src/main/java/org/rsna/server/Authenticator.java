/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.server;

import org.apache.log4j.Logger;
import org.rsna.util.Base64;

import java.util.Hashtable;

/**
 * The standard authenticator singleton class for the server.
 */
public class Authenticator {

	static final Logger logger = Logger.getLogger(Authenticator.class);

	static Authenticator authenticator = null;

	protected Hashtable<String,Session> sessions = null;
	String ssoCookieName = null;
	long timeout = 1 * 60 * 60 * 1000; //default session timeout in ms = 1 hour

	/**
	 * The protected constructor to prevent instantiation of
	 * the class except through the getInstance() method.
	 */
	protected Authenticator() {
		sessions = new Hashtable<String,Session>();
	}

	/**
	 * Get the Authenticator instance, creating it if it does not exist.
	 */
	public static synchronized Authenticator getInstance() {
		if (authenticator == null) authenticator = new Authenticator();
		return authenticator;
	}

	/**
	 * Set the Session timeout.
	 * @param timeout the timeout in milliseconds
	 */
	public synchronized void setSessionTimeout(long timeout) {
		this.timeout = timeout;
	}

	/**
	 * Set the Single Sign On cookie name. This method must
	 * be called by Users implementations that use an external
	 * SSO server for authentication. When the ssoCookieName
	 * is set, the authenticator uses the cookie value as an
	 * index into the sessions table.
	 * @param ssoCookieName the Single Sign On cookie name.
	 */
	public synchronized void setSSOCookieName(String ssoCookieName) {
		if (ssoCookieName != null) {
			ssoCookieName = ssoCookieName.trim();
			if (ssoCookieName.equals("")) ssoCookieName = null;
		}
		this.ssoCookieName = ssoCookieName;
	}

	/**
	 * Get the Session timeout.
	 */
	public synchronized long getSessionTimeout() {
		return timeout;
	}

	/**
	 * Authenticate the user from an HttpRequest.
	 * This method tests for the session cookie.
	 * If the session cookie is present, it uses it to find
	 * the session. If the session exists and applies to the
	 * request (e.g., it is from the same IP address, and it
	 * hasn't timed out), it sets the User to the User of the
	 * session. In all other cases, it sets the User to null.
	 * @param req the request.
	 * @return the authenticated user, or null if the user cannot be authenticated.
	 */
    public User authenticate(HttpRequest req) {
		Session session;

		//First try the SSO session cookie
		if (ssoCookieName != null) {
			String id = req.getCookie(ssoCookieName);
			if (id != null) {
				if ( ((session=sessions.get(id)) != null) && session.appliesTo(req) ) {
					session.recordAccess();
					return session.user;
				}
				else {
					User user = Users.getInstance().validate(req);
					if (user != null) {
						try {
							session = new Session(user, req.getRemoteAddress());
							//Note: we don't use the id created by the session
							//because the SSO system has already set the cookie,
							//so we index on that.
							sessions.put(id, session);
							session.recordAccess();
							return session.user;
						}
						catch (Exception unable) { }
					}
				}
			}
		}

		//No joy, try the RSNA session cookie
		String id = req.getCookie("RSNASESSION");
		if ( (id != null) && ((session=sessions.get(id)) != null) && session.appliesTo(req) ) {
			session.recordAccess();
			return session.user;
		}

		//No session cookie, or cookie is not valid; check the headers.
		//First try the Authorization header.
		String credentials = req.getHeader("Authorization");
		if (credentials != null) {
			String type = "basic";
			credentials = credentials.trim();
			if (credentials.toLowerCase().startsWith(type)) {
				credentials = credentials.substring(type.length()).trim();
				try {
					credentials = new String(Base64.decode(credentials), "UTF-8");
					User user = getUserFromCredentials(credentials);
					if (user != null) return user;
				}
				catch (Exception ex) { }
			}
		}

		//Next try the RSNA header. This header is not encoded.
		credentials = req.getHeader("RSNA");
		if (credentials != null) {
			User user = getUserFromCredentials(credentials);
			if (user != null) return user;
		}

		//The user cannot be authenticated.
		return null;
	}

	private User getUserFromCredentials(String credentials) {
		Users users = Users.getInstance();
		String[] up = credentials.split(":");
		User user = null;
		if (up.length == 2) {
			user = users.authenticate(up[0], up[1]);
		}
		else if (up.length == 1) {
			user = users.authenticate(up[0], "");
		}
		return user;
	}

	/**
	 * Get the username for a session identified by a session ID.
	 * @param id the ID of the session.
	 * @return the username of the session owner, or null if the
	 * session does not exist.
	 */
    public String getUsernameForSession(String id) {
		if (id != null) {
			Session session = sessions.get(id);
			if (session != null) return session.user.getUsername();
		}
		return null;
	}

	/**
	 * Create a session for a User, storing the session
	 * cookie in the supplied HttpResponse as a Set-Cookie
	 * header.
	 * @param user the user for whom to create a session.
	 * @param req the response.
	 * @param res the response.
	 * @return true if the session was created; false otherwise.
	 */
    public boolean createSession(User user, HttpRequest req, HttpResponse res) {
		try {
			Session session = new Session(user, req.getRemoteAddress());
			sessions.put(session.id, session);
			res.setHeader("Set-Cookie", "RSNASESSION="+session.id);
			res.setHeader("Cache-Control", "no-cache=\"set-cookie\"");
			return true;
		}
		catch (Exception failed) {
			return false;
		}
	}

	/**
	 * Close a session if one is identified by a session
	 * cookie in the request. Closing a session deletes the
	 * session from the sessions hashtable, making the session
	 * unrecognizable by the authenticator. It also sets
	 * a dummy session cookie with Max-Age=0.
	 * @param req the request.
	 * @param res the response.
	 */
    public void closeSession(HttpRequest req, HttpResponse res) {
		//See if there is a cookie specifying an existing session.
		String id = req.getCookie("RSNASESSION");
		if (id != null) {
			//A session was specified. Remove it from the hashtable.
			sessions.remove(id);
			//Set a dummy session cookie that expires immediately.
			res.setHeader("Set-Cookie","RSNASESSION=NONE; Max-Age=0");
			res.setHeader("Cache-Control", "no-cache=\"set-cookie\"");
		}
	}

}
