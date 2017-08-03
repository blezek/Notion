/*---------------------------------------------------------------
*  Copyright 2011 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.server;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

/**
 * A class to extend the org.rsna.server.UsersLdapFileImpl class
 * for testing.
 */
public class UsersTestLdapFileImpl extends UsersLdapFileImpl {

	static final Logger logger = Logger.getLogger(UsersTestLdapFileImpl.class);

	/**
	 * Constructor.
	 * @param element the Server element from the configuration.
	 */
	public UsersTestLdapFileImpl(Element element) {
		super(element);
	}

	/**
	 * Accept any user whose username exists in the users.xml file,
	 * regardless of the password.
	 */
	@Override
  public User authenticate(String username, String password) {
		return getUser(username);
	}

}