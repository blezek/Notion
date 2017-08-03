package org.rsna.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * An extension of java.util.Properties.
 */
public class PropertiesFile extends Properties {

	File file;

	/**
	 * Class constructor; creates a new Properties object and
	 * loads the properties file, ignoring exceptions.
	 *
	 * @param file the properties file.
	 */
	public PropertiesFile(File file) {
		super();
		this.file = file;
		load();
	}

	/**
	 * Get the file.
	 * @return the file.
	 */
	public File getFile() {
		return file;
	}

	/**
	 * Set the file without loading it.
	 */
	public void setFile(File file) {
		this.file = file;
	}

	/**
	 * Load the properties file.
	 * @return true if the load was successful; false otherwise.
	 */
	public boolean load() {
		FileInputStream stream = null;
		try {
			stream = new FileInputStream(file);
			super.load(stream);
			stream.close();
			return true;
		}
		catch (Exception e) {
			if (stream != null) {
				try { stream.close(); }
				catch (Exception ignore) { }
			}
			return false;
		}
	}

	/**
	 * Save the properties file.
	 * @return true if the save was successful; false otherwise.
	 */
	public boolean store() {
		FileOutputStream stream = null;
		try {
			stream = new FileOutputStream(file);
			super.store(stream, file.getName());
			stream.flush();
			stream.close();
			return true;
		}
		catch (Exception e) {
			if (stream != null) {
				try { stream.close(); }
				catch (Exception ignore) { }
			}
			return false;
		}
	}

}
