/*---------------------------------------------------------------
*  Copyright 2011 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.server;

import org.apache.log4j.Logger;
import org.rsna.util.LdapUtil;
import org.rsna.util.StringUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Element;

import java.util.Properties;

/**
 * A class to extend the org.rsna.server.UsersXmlFileImpl class
 * for managing the users.xml file while authenticating credentials
 * via an LDAP server.
 */
public class UsersLdapFileImpl extends UsersXmlFileImpl {

	static final Logger logger = Logger.getLogger(UsersLdapFileImpl.class);

	String initialContextFactory = "";
	String providerURL = "";
	String securityAuthentication = "simple";
	String securityPrincipal = "";

	/**
	 * Constructor.
	 * @param element the Server element from the configuration.
	 */
	public UsersLdapFileImpl(Element element) {
		super(element);
		Element ldap = XmlUtil.getFirstNamedChild(element, "LDAP");
		if (ldap != null) {
			initialContextFactory = ldap.getAttribute("initialContextFactory");
			providerURL = ldap.getAttribute("providerURL");
			securityAuthentication = ldap.getAttribute("securityAuthentication");
			securityPrincipal = ldap.getAttribute("securityPrincipal");

			//Make sure we have an admin user who is known to LDAP
			String ldapAdmin = ldap.getAttribute("ldapAdmin").trim();
			if (!ldapAdmin.equals("")) {
				User admin = getUser(ldapAdmin);
				if (admin == null) {
					admin = new User(ldapAdmin, "");
					logger.info("\""+ldapAdmin+"\" admin user created");
				}
				admin.addRole("admin");
				addUser(admin);
			}
		}
		else logger.warn("Missing LDAP element - no parameters are available for initialization");
	}

	/**
	 * Check whether a set of credentials match a user in the system.
	 * To be authenticated, a user must appear in the users.xml file
	 * <b>and</b> the user's credentials must be accepted by the LDAP server.
	 * @return true if the credentials match a user; false otherwise.
	 */
	public User authenticate(String username, String password) {

		User user = getUser(username);
		if (user != null) {

			Properties props = new Properties();
			props.setProperty( "username", username );
			String principal = StringUtil.replace( securityPrincipal, props );

			if ( LdapUtil.authenticate(
							initialContextFactory,
							providerURL,
							securityAuthentication,
							principal,
							password) ) {
				if (logger.isDebugEnabled()) {
					String xml = XmlUtil.toPrettyString(user.getXML(false)); //don't log the password
					logger.debug("LDAP has authenticated user \""+username+"\"\n"+xml);
				}
				return user;
			}
			else {
				logger.debug("LDAP failed to authenticate user \""+username+"\".");
				return null;
			}
		}
		logger.debug("Unable to find user \""+username+"\" in the users.xml file.");
		return null;
	}
}