/*---------------------------------------------------------------
*  Copyright 2009 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.util;

import org.w3c.dom.Element;

/**
 * A singleton class encapsulating a proxy server.
 */
public class SSLConfiguration {

	static final String keystoreProp			= "javax.net.ssl.keyStore";
	static final String keystorePasswordProp	= "javax.net.ssl.keyStorePassword";
	static final String truststoreProp			= "javax.net.ssl.trustStore";
	static final String truststorePasswordProp	= "javax.net.ssl.trustStorePassword";

	String keystore			= "";
	String keystorePassword	= "";
	String truststore		= "";
	String truststorePassword = "";

	static SSLConfiguration sslConfiguration = null;

	protected SSLConfiguration(String keystore, String keystorePassword,
							   String truststore, String truststorePassword) {

		this.keystore = StringUtil.replace( StringUtil.trim(keystore), System.getProperties(), true ).trim();
		this.keystorePassword = StringUtil.trim(keystorePassword);

		this.truststore = StringUtil.replace( StringUtil.trim(truststore), System.getProperties(), true ).trim();
		this.truststorePassword = StringUtil.trim(truststorePassword);

		if (keystore.equals("")) this.keystore = "keystore";
		if (keystorePassword.equals("")) this.keystorePassword = "ctpstore";
	}


	/**
	 * Set a new SSLConfiguration, creating an instance of the SSLConfiguration
	 * class from a DOM element containing four attributes:
	 * <ul>
	 * <li>keystore
	 * <li>keystorePassword
	 * <li>truststore
	 * <li>truststorePassword
	 * </ul>
	 * @param element the element specifying the SSL parameters.
	 * @return the singleton instance of the SSLConfiguration.
	 */
	public static synchronized SSLConfiguration getInstance(Element element) {
		if (sslConfiguration == null) {
			if (!element.getTagName().equals("SSL")) {
				element = XmlUtil.getFirstNamedChild(element, "SSL");
			}
			if (element != null) {
				return getInstance(
							element.getAttribute("keystore"),
							element.getAttribute("keystorePassword"),
							element.getAttribute("truststore"),
							element.getAttribute("truststorePassword") );
			}
			else return getInstance("", "", "", "");
		}
		return sslConfiguration;
	}

	/**
	 * Set a new SSLConfiguration, creating an instance of the SSLConfiguration
	 * class from four Strings.
	 * @param keystore the path to the keystore file
	 * @param keystorePassword the password to the keystore.
	 * @param truststore the path to the truststore file
	 * @param truststorePassword the password to the truststore.
	 * @return the singleton instance of the SSLConfiguration.
	 */
	public static synchronized SSLConfiguration getInstance(
													String keystore, String keystorePassword,
						  							String truststore, String truststorePassword) {
		sslConfiguration = new SSLConfiguration(
									keystore, keystorePassword,
									truststore, truststorePassword);
		return sslConfiguration;
	}

	/**
	 * Get the singleton instance of the SSLConfiguration, or null if one doesn't exist.
	 * @return the singleton instance of the SSLConfiguration.
	 */
	public static synchronized SSLConfiguration getInstance() {
		return sslConfiguration;
	}

	/**
	 * Set the System properties based on the parameters in the SSLConfiguration.
	 */
	public void setSystemParameters() {
		if (!keystore.equals("")) {
			System.setProperty(keystoreProp, keystore);
			System.setProperty(keystorePasswordProp, keystorePassword);
		}
		else {
			System.clearProperty(keystoreProp);
			System.clearProperty(keystorePasswordProp);
		}

        if (!truststore.equals("")) {
			System.setProperty(truststoreProp, truststore);
			System.setProperty(truststorePasswordProp, truststorePassword);
		}
		else {
			System.clearProperty(truststoreProp);
			System.clearProperty(truststorePasswordProp);
		}
	}
}

