/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.server;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import java.lang.reflect.Constructor;

/**
 * A singleton class for managing users.
 * The getInstance method of this class is used to obtain an instance that
 * matches the standard implementation. The purpose of this approach is to make
 * it easy to switch methods of managing users without affecting other code.
 */
public abstract class Users {

	static final Logger logger = Logger.getLogger(Users.class);

	protected static Users users = null;

	/**
	 * Protected class constructor to prevent anyone from instantiating this class.
	 */
    protected Users() {	}

	/**
	 * Get a Users instance from the specified class.
	 * @param className the fully qualified name of the class to
	 * instantiate if a Users instance does not already exist.
	 * If the className is null or blank, no class is instantiated.
	 * @param element the element from which to obtain any required
	 * configuration parameters
	 * @return the Users object or null if it cannot be instantiated from
	 * the supplied className.
	 */
	public static synchronized Users getInstance(String className, Element element) {
		if ((users == null) && (className != null) && !className.trim().equals("")) {
			try {
				Class theClass = Class.forName(className);
				Class[] signature = { Element.class };
				Constructor constructor = theClass.getConstructor(signature);
				Object[] args = { element };
				users = (Users)constructor.newInstance(args);

				//Add the administrative roles
				users.addRole("admin");
				users.addRole("shutdown");
			}
			catch (Exception ex) {
				logger.warn("Unable to load the Users class: " + className, ex);
			}
		}
		return users;
	}

	/**
	 * Get the current Users instance. This method does not instantiate
	 * a Users subclass. If it has not already been instantiated, null
	 * is returned.
	 * @return the Users object or null if no Users instance exists.
	 */
	public static synchronized Users getInstance() {
		return users;
	}

	/**
	 * Get a specific user.
	 * @return the user or null if unable.
	 */
	public abstract User getUser(String username);

	/**
	 * Convert a plaintext password to the form used by this implementation.
	 * @param password the plaintext password
	 * @return the password converted to the form used by this implementation.
	 */
	public abstract String convertPassword(String password);

	/**
	 * Check whether a set of credentials match a user in the system.
	 * @return the user who matches the credentials, or null if no matching user exists.
	 */
	public abstract User authenticate(String username, String password);

	/**
	 * Check whether a request comes from a user known to an external system.
	 * This implementation returns null, indicating that no external system can
	 * associate a user with this request. Single Sign On implementations must
	 * override this method.
	 * @return the user who matches the credentials, or null if no matching user exists.
	 */
	public User validate(HttpRequest req) {
		return null;
	}

	/**
	 * Get an alphabetized array of usernames.
	 * @return the array of usernames or a zero-length array if unable.
	 */
	public abstract String[] getUsernames();

	/**
	 * Add a role to the list of standard roles.
	 * @param role the name of the role.
	 */
	public abstract void addRole(String role);

}
