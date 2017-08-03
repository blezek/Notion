/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.server;

import org.apache.log4j.Logger;
import org.rsna.util.Base64;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.HashSet;

/**
 * A class to encapsulate an authenticated user.
 */
public class User implements Comparable<User> {

	static final Logger logger = Logger.getLogger(User.class);

	String username;
	String password;
	HashSet<String> roles;

	/**
	 * Class constructor; creates a new User with a username, a password, and no roles.
	 * @param username the username.
	 * @param password the password.
	 */
    public User(String username, String password) {
		this.username = username;
		this.password = password;
		this.roles = new HashSet<String>();
	}

	/**
	 * The Comparable interface implementation.
	 * @param user the user to compare.
	 * @return this.username.compareTo(user.getUsername())
	 */
    @Override
    public int compareTo(User user) {
		return this.username.compareTo(user.getUsername());
	}

	/**
	 * Add a role.
	 * @param role the role to add.
	 * @return true if the set of roles changed as a result of this method call; false
	 * if the set did not change (because the role was already present and it was not
	 * necessary to add it).
	 */
    public boolean addRole(String role) {
		return roles.add(role);
	}

	/**
	 * Remove a role.
	 * @param role the role to remove.
	 * @return true if the set of roles changed as a result of this method call; false
	 * if the set did not change (because the role was not present to be removed).
	 */
    public boolean removeRole(String role) {
		return roles.remove(role);
	}

	/**
	 * Get the user's name.
	 */
    public String getUsername() {
		return username;
	}

	/**
	 * Get the user's password.
	 */
    public String getPassword() {
		return password;
	}

	/**
	 * Set the user's password.
	 */
    public void setPassword(String password) {
		this.password = password;
	}

	/**
	 * Test whether a user has a specific role.
	 * @return true if the user has the specified role; false otherwise.
	 */
    public boolean hasRole(String role) {
		return roles.contains(role);
	}

	/**
	 * Get the user's roles HashSet.
	 */
    public HashSet<String> getRoles() {
		return roles;
	}

	/**
	 * Get the user's role names (unsorted).
	 */
    public String[] getRoleNames() {
		String[] names = new String[roles.size()];
		names = roles.toArray(names);
		return names;
	}

	/**
	 * Get the username and password in the form for
	 * a basic authorization header: "Basic " + Base64(username:password).
	 * @return the basic authorization string.
	 */
	public String getBasicAuthorization() {
		return "Basic " + Base64.encodeToString((username + ":" + password).getBytes());
	}

	/**
	 * Make an XML Document for the user. The format of the document is:<br><br>
	 * &lt;user username="..." password="..."&gt;<br>
	 * &nbsp;&nbsp;&nbsp;&lt;role&gt;rolename&lt;/role&gt; (one element per role)<br>
	 * &lt;/user&gt;<br>
	 * @param includePassword true if the password is to be included in the document; false otherwise.
	 * @return an XML document containing the username, password, and role elements.
	 */
	public Document getXML(boolean includePassword) {
		try {
			Document doc = XmlUtil.getDocument();
			Element user = doc.createElement("user");
			doc.appendChild(user);
			user.setAttribute("username",username);
			if (includePassword) user.setAttribute("password",password);
			for (String rolename : roles) {
				Element role = doc.createElement("role");
				role.appendChild(doc.createTextNode(rolename));
				user.appendChild(role);
			}
			return doc;
		}
		catch (Exception ex) { return null; }
	}
}
