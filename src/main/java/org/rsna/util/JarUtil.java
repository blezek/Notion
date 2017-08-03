/*---------------------------------------------------------------
*  Copyright 2009 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.util;

import java.io.File;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * A class encapsulating methods to access a JAR file.
 */
public class JarUtil {

	/**
	 * Get all the main attributes from the manifest of
	 * a jar and return them in a hashtable.
	 */
	public static Hashtable<String,String> getManifestAttributes(File jarFile) {
		Hashtable<String,String> h = new Hashtable<String,String>();
		JarFile jar = null;
		try {
			jar = new JarFile(jarFile);
			Manifest manifest = jar.getManifest();
			Attributes attrs = manifest.getMainAttributes();
			Iterator it = attrs.keySet().iterator();
			while (it.hasNext()) {
				String key = it.next().toString();
				h.put(key, attrs.getValue(key));
			}
		}
		catch (Exception ex) { }
		FileUtil.close(jar);
		return h;
	}

}

