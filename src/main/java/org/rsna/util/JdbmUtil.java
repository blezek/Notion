/*---------------------------------------------------------------
*  Copyright 2009 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.util;

import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Properties;

import jdbm.RecordManager;
import jdbm.RecordManagerFactory;
import jdbm.RecordManagerOptions;
import jdbm.btree.BTree;
import jdbm.htree.HTree;

/**
 * Encapsulates helper methods for using JDBM.
 */
public class JdbmUtil {

	static final Logger logger = Logger.getLogger(JdbmUtil.class);

	/**
	 * Get a RecordManager for a specified database file.
	 * @param indexPath the path to the database file. The path
	 * may be absolute or relative, and it must end in a file name
	 * (generally with no extension, since the RecordManager
	 * creates two files, one with the ".db" extension to hold the
	 * data, and one with the ".lg" extension to hold the transaction
	 * log).
	 * @return the RecordManager, or null if one cannot be created.
	 */
	public static RecordManager getRecordManager(String indexPath) {
		RecordManager recman = null;
		try {
			Properties props = new Properties();
			props.put( RecordManagerOptions.THREAD_SAFE, "true" );
			recman = RecordManagerFactory.createRecordManager( indexPath, props );
		}
		catch (Exception e) {
			logger.error("Unable to obtain the RecordManager for "+indexPath);
		}
		return recman;
	}

	/**
	 * See if a database contains an object with a specified name. The name
	 * can be that of an HTree or BTree.
	 * @param recman the RecordManager of the database in which to find the object.
	 * @param name the name of the object.
	 * @return true if the database contains the object, false otherwise.
	 */
	public static boolean containsNamedObject(RecordManager recman, String name) {
		try {
			long recid = recman.getNamedObject(name);
			return (recid != 0);
		}
		catch (Exception notThere) { return false; }
	}

	/**
	 * Delete a named object from a database, ignoring any errors. The name
	 * can be that of an HTree or BTree.
	 * @param recman the RecordManager of the database in which to remove the object.
	 * @param name the name of the object to delete.
	 */
	public static void deleteNamedObject(RecordManager recman, String name) {
		try {
			long recid = recman.getNamedObject(name);
			if (recid != 0) recman.delete(recid);
		}
		catch (Exception ignore) { }
	}

	/**
	 * Get a named HTree, or create it if it doesn't exist.
	 * @param recman the RecordManager of the database in which to get the HTree.
	 * @param name the name of the HTree.
	 * @return the HTree, or null if one cannot be created.
	 */
	public static HTree getHTree(RecordManager recman, String name) {
		HTree index = null;
		try {
			long recid = recman.getNamedObject(name);
			if ( recid != 0 ) {
				index = HTree.load( recman, recid );
			}
			else {
				index = HTree.createInstance( recman );
				recman.setNamedObject( name, index.getRecid() );
				recman.commit();
			}
		}
		catch (Exception ex) { logger.warn("Unable to find or create the HTree \""+name+"\""); }
		return index;
	}

	/**
	 * Get a named BTree, or create it if it doesn't exist.
	 * The BTree is created with a Comparator that puts String
	 * keys in alphabetical order and Integer and Long keys in numeric order.
	 * @param recman the RecordManager of the database in which to get the BTree.
	 * @param name the name of the BTree.
	 * @return the BTree, or null if one cannot be created.
	 */
	public static BTree getBTree(RecordManager recman, String name) {
		BTree index = null;
		try {
			long recid = recman.getNamedObject(name);
			if ( recid != 0 ) {
				index = BTree.load( recman, recid );
			}
			else {
				index = BTree.createInstance( recman, new KeyComparator() );
				recman.setNamedObject( name, index.getRecid() );
				recman.commit();
			}
		}
		catch (Exception ex) { logger.warn("Unable to find or create the BTree \""+name+"\""); }
		return index;
	}

	static class KeyComparator implements Comparator, Serializable {
		static final long serialVersionUID = 1L;
		public KeyComparator() { }
		public int compare(Object key1, Object key2) {
			if ( (key1 instanceof String) && (key2 instanceof String)) {
				return ((String)key1).compareTo((String)key2);
			}
			else if ( (key1 instanceof Integer) && (key2 instanceof Integer)) {
				return ((Integer)key1).compareTo((Integer)key2);
			}
			else if ( (key1 instanceof Long) && (key2 instanceof Long)) {
				return ((Long)key1).compareTo((Long)key2);
			}
			else return 0;
		}
		public boolean equals(Object obj) {
			return this.equals(obj);
		}
	}

	/**
	 * Commit and close a database, ignoring errors..
	 * @param recman the RecordManager of the database to commit and close.
	 */
	public static void close(RecordManager recman) {
		if (recman != null) {
			try {
				recman.commit();
				recman.close();
			}
			catch (Exception ignore) { }
		}
	}

}

