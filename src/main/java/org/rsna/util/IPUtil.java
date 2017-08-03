/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * A class for getting the IP address of the host computer.
 */
public class IPUtil {

	static final String def = "127.0.0.1";

	/**
	 * Get the IP address of the host computer, or the loopback address
	 * (127.0.0.1) if the operation fails.
	 * @return the IP Address string.
	 */
	public static String getIPAddress() {
		try {
			//Get all the network interfaces
			Enumeration<NetworkInterface> nwEnum = NetworkInterface.getNetworkInterfaces();

			//Return the first IPv4 address that is not a loopback address.
			while (nwEnum.hasMoreElements()) {
				NetworkInterface nw = nwEnum.nextElement();
				Enumeration<InetAddress> ipEnum = nw.getInetAddresses();
				while (ipEnum.hasMoreElements()) {
					InetAddress ina = ipEnum.nextElement();
					if ((ina instanceof Inet4Address) && !ina.isLoopbackAddress()) {
						return ina.getHostAddress();
					}
				}
			}
		}
		catch (Exception ignore) { }
		return def;
	}

}

