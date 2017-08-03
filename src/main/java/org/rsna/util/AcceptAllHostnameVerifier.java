/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.util;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

/**
 * An All-verifying Hostname Verifier.
 */
public class AcceptAllHostnameVerifier implements HostnameVerifier {

	@Override
  public boolean verify(String urlHost, SSLSession ssls) {
		return true;
	}

}

