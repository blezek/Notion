/*---------------------------------------------------------------
*  Copyright 2009 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.util;

import org.w3c.dom.Element;

import java.util.Properties;

/**
 * A singleton class encapsulating a proxy server.
 */
public class ProxyServer {

	String proxyIPAddress	= "";
	String proxyPort		= "";
	String proxyUsername	= "";
	String proxyPassword	= "";

	static ProxyServer proxyServer = null;

	protected ProxyServer(String proxyIPAddress, String proxyPort,
						  String proxyUsername, String proxyPassword) {
		this.proxyIPAddress = StringUtil.trim(proxyIPAddress);
		this.proxyPort 		= StringUtil.trim(proxyPort);
		this.proxyUsername 	= StringUtil.trim(proxyUsername);
		this.proxyPassword 	= StringUtil.trim(proxyPassword);
	}

	/**
	 * Get the ProxyServer, or create a new one if one doesn't exist.
	 * If necessary, this method creates an instance of the ProxyServer
	 * class from a DOM element containing four attributes:
	 * <ul>
	 * <li>proxyIPAddress
	 * <li>proxyPort
	 * <li>proxyUsername
	 * <li>proxyPassword
	 * </ul>
	 * @param element the element specifying the proxy parameters. The
	 * element must have a first-generation child element with the tag
	 * name "ProxyServer" containing the proxy attributes.
	 * @return the singleton instance of the proxy server.
	 */
	public static synchronized ProxyServer getInstance(Element element) {
		if (proxyServer == null) {
			if (!element.getTagName().equals("ProxyServer")) {
				element = XmlUtil.getFirstNamedChild(element, "ProxyServer");
			}
			if (element != null) {
				return getInstance(
							element.getAttribute("proxyIPAddress"),
							element.getAttribute("proxyPort"),
							element.getAttribute("proxyUsername"),
							element.getAttribute("proxyPassword") );
			}
			else return getInstance("", "", "", "");
		}
		return proxyServer;
	}

	/**
	 * Get the ProxyServer, or create a new one if one doesn't exist.
	 * If necessary, this method creates an instance of the ProxyServer
	 * class from four Strings.
	 * @param proxyIPAddress the IP address of the proxy server
	 * @param proxyPort the port of the proxy server
	 * @param proxyUsername the username to be provided to the proxy
	 * server if proxy authentication is required.
	 * @param proxyPassword the password to be provided to the proxy
	 * server if proxy authentication is required.
	 * @return the singleton instance of the proxy server.
	 */
	public static synchronized ProxyServer getInstance(String proxyIPAddress, String proxyPort,
						  			  String proxyUsername, String proxyPassword) {
		if (proxyServer == null) {
			proxyServer =
				new ProxyServer(
						proxyIPAddress, proxyPort, proxyUsername, proxyPassword);
		}
		return proxyServer;
	}

	/**
	 * Get the singleton instance of the ProxyServer, or null if one doesn't exist.
	 * @return the singleton instance of the proxy server.
	 */
	public static synchronized ProxyServer getInstance() {
		return proxyServer;
	}

	/**
	 * Set the System properties based on the parameters in the ProxyServer.
	 * If the proxy is enabled, set the System properties.
	 * If the proxy is not enabled, clear the System properties.
	 * @return the encoded proxy credentials or the empty string if
	 * the proxy is not enabled or if proxy credentials are not required.
	 */
	public String setSystemParameters() {
		if (getProxyEnabled()) {
			setProxyProperties();
			return (authenticate() ? getEncodedCredentials() : "");
		}
		clearProxyProperties();
		return "";
	}

	/**
	 * Determine whether the proxy parameters indicate that the
	 * proxy server is enabled.
	 */
	public boolean getProxyEnabled() {
		return !proxyIPAddress.equals("") && !proxyPort.equals("");
	}

	/**
	 * Determine whether the parameters indicate
	 * that proxy user authentication is to be used.
	 */
	public boolean authenticate() {
		return getProxyEnabled() && !proxyUsername.equals("") && !proxyPassword.equals("");
	}

	/**
	 * Set the Java System properties that apply to the proxy server.
	 */
	public void setProxyProperties() {
		System.setProperty("proxySet","true");
		System.setProperty("http.proxyHost",proxyIPAddress);
		System.setProperty("http.proxyPort",proxyPort);
	}

	/**
	 * Clear the Java System properties that apply to the proxy server.
	 */
	public void clearProxyProperties() {
		Properties sys = System.getProperties();
		sys.remove("proxySet");
		sys.remove("http.proxyHost");
		sys.remove("http.proxyPort");
	}

	/**
	 * Get the base-64 encoded value of the proxy user authentication credentials
	 * in the form required for an HTTP Proxy-Authorization header:
	 */
	public String getEncodedCredentials() {
		return Base64.encodeToString((proxyUsername + ":" + proxyPassword).getBytes());
	}
}

