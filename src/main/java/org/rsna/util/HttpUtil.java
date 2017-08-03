/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.util;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;

/**
 * A class to encapsulate HTTP utilities.
 */
public class HttpUtil {

	/**
	 * Get an HttpURLConnection for a specified URL String. The
	 * connection is returned set up for input and output, with
	 * any required proxy authentication parameters included,
	 * and with the the request method set to "POST" and the
	 * content type set to "application/x-mirc".
	 * (These can be overwritten if necessary.)
	 * @param urlString the absolute URL, starting with the protocol.
	 * @return the HttpURLConnection.
	 * @throws Exception if the protocol is neither "http" nor "https",
	 * or if an error occurs in initializing the connection.
	 */
	public static HttpURLConnection getConnection(String urlString) throws Exception {
		URL url = new URL(urlString);
		return getConnection(url);
	}


	/**
	 * Get an HttpURLConnection for a specified URL. The
	 * connection is returned set up for input and output, with
	 * any required proxy authentication parameters included,
	 * and with the the request method set to "POST" and the
	 * content type set to "application/x-mirc".
	 * (These can be overwritten if necessary.)
	 * @param url the absolute URL, including the protocol.
	 * @return the HttpURLConnection.
	 * @throws Exception if the protocol is neither "http" nor "https",
	 * or if an error occurs in initializing the connection.
	 */
	public static HttpURLConnection getConnection(URL url) throws Exception {

		String protocol = url.getProtocol().toLowerCase();
		if (!protocol.startsWith("https") && !protocol.startsWith("http")) {
			throw new Exception("Unsupported protocol ("+protocol+")");
		}

		HttpURLConnection conn;
		if (protocol.startsWith("https")) {
			HttpsURLConnection httpsConn = (HttpsURLConnection)url.openConnection();
			httpsConn.setHostnameVerifier(new AcceptAllHostnameVerifier());
			httpsConn.setUseCaches(false);
			httpsConn.setDefaultUseCaches(false);

			//Set the socket factory
			TrustManager[] trustAllCerts = new TrustManager[] { new AcceptAllX509TrustManager() };
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new SecureRandom());
			httpsConn.setSSLSocketFactory(sc.getSocketFactory());

			conn = httpsConn;
		}
		else conn = (HttpURLConnection)url.openConnection();

		conn.setDoOutput(true);
		conn.setDoInput(true);
		conn.setRequestMethod("POST");
		conn.setRequestProperty("Content-Type", "application/x-mirc");

		//If the proxy is enabled and proxy authentication
		//credentials are available, set them in the request.
		ProxyServer proxy = ProxyServer.getInstance();
		if ((proxy != null) && proxy.authenticate()) {
			conn.setRequestProperty(
				"Proxy-Authorization",
				"Basic "+proxy.getEncodedCredentials());
		}

		//and return the connection.
		return conn;
	}
}
