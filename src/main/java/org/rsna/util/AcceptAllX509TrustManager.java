/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.util;

import javax.net.ssl.X509TrustManager;

import java.security.cert.X509Certificate;

/**
 * An All-accepting X509 Trust Manager.
 */
public class AcceptAllX509TrustManager implements X509TrustManager {

	public X509Certificate[] getAcceptedIssuers() {
		return null;
	}

	public void checkClientTrusted(X509Certificate[] certs, String authType) { }

	public void checkServerTrusted(X509Certificate[] certs, String authType) { }
}

