/*---------------------------------------------------------------
*  Copyright 2012 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.util;

import java.io.File;

/**
 * A singleton cache for files that are served from the classpath jars.
 */
public class Cache {

	static Cache cache = null;
	static File dir = null;

	/**
	 * The protected constructor to prevent instantiation of
	 * the class except through the getInstance() method.
	 * @param dir the base directory of the cache.
	 */
	protected Cache(File dir) {
		Cache.dir = dir;
	}

	/**
	 * Get the Cache instance, creating it if it does not exist.
	 * This method is intended only for use during initialization.
	 * If the cache has already been created, this method simply
	 * returns the existing instance; it does not change the
	 * cache directory.
	 * @param dir the base directory of the cache.
	 * @return the Cache instance.
	 */
	public synchronized static Cache getInstance(File dir) {
		if (cache == null) cache = new Cache(dir);
		return cache;
	}

	/**
	 * Get the Cache instance, returning null if it does not exist.
	 * @return the Cache instance, or null, if the Cache has not
	 * been instantiated..
	 */
	public synchronized static Cache getInstance() {
		return cache;
	}

	/**
	 * Clear the cache.
	 */
	public synchronized void clear() {
		FileUtil.deleteAll(dir);
	}

	/**
	 * Get a File pointing to a resource in the cache, loading the
	 * resource from the classpath if the resource is not in the cache.
	 * @param path the relative path to the resource. This is the same path
	 * that is used to load the resource if it does not exist in the cache.
	 * @return the File pointing to the resource in the cache, or null if
	 * the resource cannot be obtained from the classpath.
	 */
    public synchronized File getFile(String path) {
		if (path.startsWith("/")) path = path.substring(1);
		File file = new File(dir, path);
		return FileUtil.getFile(file, path);
	}
}
