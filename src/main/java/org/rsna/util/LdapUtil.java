/*---------------------------------------------------------------
*  Copyright 2011 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.util;

import javax.naming.Context;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import org.apache.log4j.Logger;

import java.util.Hashtable;

/**
 * A helper class for authenticating users with an LDAP server.
 */
public class LdapUtil {

	static final Logger logger = Logger.getLogger(LdapUtil.class);
	static final String defaultContextFactory = "com.sun.jndi.ldap.LdapCtxFactory";
	static final String defaultSecurityAuthentication = "simple";

	/**
	 * Authenticate a securityPrincipal with an LDAP provider
	 * @param initialContextFactory the factory class (e.g., "com.sun.jndi.ldap.LdapCtxFactory")
	 * @param providerURL the URL of the provider (e.g., "ldap://ip:port/path")
	 * @param securityAuthentication the authentication type (e.g., "simple")
	 * @param securityPrincipal the username (e.g., "cn=username, ou=NewHires, o=JNDITutorial"
	 * @param securityCredentials the password (e.g., "mysecret")
	 * @return true if the authentication succeeds; false otherwise.
	 */
    public static boolean authenticate(
			String initialContextFactory,
			String providerURL,
			String securityAuthentication,
			String securityPrincipal,
			String securityCredentials) {

		initialContextFactory = initialContextFactory.trim();
		initialContextFactory = (initialContextFactory.equals("") ? defaultContextFactory : initialContextFactory);

		securityAuthentication = securityAuthentication.trim();
		securityAuthentication = (securityAuthentication.equals("") ? defaultSecurityAuthentication : securityAuthentication);

		Hashtable<String,String> env = new Hashtable<String,String>();
		env.put(Context.INITIAL_CONTEXT_FACTORY, initialContextFactory);
		env.put(Context.PROVIDER_URL, providerURL);
		env.put(Context.SECURITY_AUTHENTICATION, securityAuthentication);
		env.put(Context.SECURITY_PRINCIPAL, securityPrincipal);
		env.put(Context.SECURITY_CREDENTIALS, securityCredentials);
	    env.put("java.naming.ldap.derefAliases", "never");

		DirContext ctx = null;
		boolean result = true;
		try {
			ctx = new InitialDirContext(env);
			logger.debug("Got InitialDirContext: class = "+ctx.getClass().getName());
		}
		catch (Exception ex) {
			result = false;
			logger.debug("Unable to get InitialDirContext: "+ex.getMessage());
		}
		if (ctx != null) {
			try { ctx.close(); }
			catch (Exception ex) { }
		}
		logger.debug("Returning authentication result: "+result);
		return result;
    }
}
