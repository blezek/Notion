/*---------------------------------------------------------------
*  Copyright 2009 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.util;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedList;

/**
 * A ClassLoader that finds classes in an array of JARs,
 * giving precedence in delegation to the JARs.
 */
public class JarClassLoader extends URLClassLoader {

	/**
	 * Get a JarClassLoader initialized to a set of files and directories.
	 * This method places all individual files in the array of JARs.
	 * It searches any directories to find all the files that end in ".jar"
	 * (not case sensitive) and places them in the array as well.
	 * @param files the array of File objects to include in the array of JARs.
	 * The items in the array can be individual JAR files or directories.
	 * @return a JarClassLoader initialized to the set of JARs found in the
	 * files array.
	 */
	public static JarClassLoader getInstance(File[] files) {
		LinkedList<URL> urlList = new LinkedList<URL>();
		for (int i=0; i<files.length; i++) {
			if (files[i].exists()) {
				if (files[i].isFile()) {
					try { urlList.add( files[i].toURI().toURL() ); }
					catch (Exception skip) {
						System.out.println("Unable to add file to classpath: "+files[i]);
					}
				}
				else if (files[i].isDirectory()) {
					File[] jars = files[i].listFiles();
					for (int k=0; k<jars.length; k++) {
						if (jars[k].getName().toLowerCase().endsWith(".jar")) {
							try { urlList.add( jars[k].toURI().toURL() ); }
							catch (Exception skip) {
								System.out.println("Unable to add file to classpath: "+jars[k]);
							}
						}
					}
				}
			}
		}
		URL[] urls = new URL[urlList.size()];
		urls = urlList.toArray(urls);
		return new JarClassLoader(urls);
	}

	public JarClassLoader(URL[] urls) {
		super(urls);
	}

	public JarClassLoader(URL[] urls, ClassLoader parent) {
		super(urls, parent);
	}

	@Override
  protected synchronized Class loadClass(String classname, boolean resolve)
			throws ClassNotFoundException {

		Class theClass = findLoadedClass(classname);
		if (theClass != null) return theClass;

		//If it looks like a system class, try the parent first.
		if (classname.startsWith("java.") || classname.startsWith("javax.")) {
			try { theClass = findBaseClass(classname); }
			catch (ClassNotFoundException cnfe) {
				theClass = findClass(classname);
			}
		}

		//If it didn't look like a system class, then try the jars first.
		//This violates the normal delegation mechanism, but it is done
		//to ensure that this classloader becomes the classloader of all
		//classes that it can load, even those that could have been loaded
		//by the application classloader from the classpath.
		else {
			try { theClass = findClass(classname); }
			catch (ClassNotFoundException cnfe) {
				theClass = findBaseClass(classname);
			}
		}
		if (resolve) { resolveClass(theClass); }
		return theClass;
	}

	private Class findBaseClass(String name) throws ClassNotFoundException {
		return (getParent() == null) ? findSystemClass(name) : getParent().loadClass(name);
	}

}
