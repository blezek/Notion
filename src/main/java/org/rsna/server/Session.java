/*---------------------------------------------------------------
 *  Copyright 2005 by the Radiological Society of North America
 *
 *  This source software is released under the terms of the
 *  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
 *----------------------------------------------------------------*/

package org.rsna.server;

import java.math.BigInteger;
import java.security.MessageDigest;

/**
 * Class to encapsulate a session.
 */
public class Session {
	public long lastAccess;
	public final User user;
	public final String ipAddress;
	public final String id;

	//Construct a Session
	public Session(User user, String ipAddress) throws Exception {
		lastAccess = System.currentTimeMillis();
		this.user = user;
		this.ipAddress = ipAddress;
		id = getSessionID(user.getUsername(), ipAddress);
	}

	public boolean appliesTo(HttpRequest req) {
		long timeout = Authenticator.getInstance().getSessionTimeout();
		boolean ok = req.getRemoteAddress().equals(ipAddress);
		if (ok && (timeout > 0)) {
			ok &= ((System.currentTimeMillis() - lastAccess) < timeout);
		}
		return ok;
	}

	public void recordAccess() {
		lastAccess = System.currentTimeMillis();
	}

	//Make a session ID by hashing the username, the IP address and the current time.
	private String getSessionID(String username, String ipAddress) throws Exception {
		String string = username + ":" + ipAddress + ":" + System.currentTimeMillis();
		MessageDigest messageDigest = MessageDigest.getInstance("MD5");
		byte[] hashed = messageDigest.digest(string.getBytes("UTF-8"));
		BigInteger bi = new BigInteger(1, hashed);
		return bi.toString();
	}
}
