/*---------------------------------------------------------------
*  Copyright 2010 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.util;

import org.apache.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

/**
 * A class encapsulating methods to serialize a class to a file.
 */
public class SerializerUtil {

	static final Logger logger = Logger.getLogger(SerializerUtil.class);

	/**
	 * Deserialize an object stored in a specified file.
	 * @param file the file containing the object.
	 * @return the deserialized instance.
	 */
	public static Object deserialize(File file) {
		if ((file == null) || !file.exists()) return null;
		ObjectInputStream in = null;
		try {
			in = new ObjectInputStream(new FileInputStream(file));
			Object object = in.readObject();
			in.close();
			return object;
		}
		catch (Exception ex) {
			logger.warn("Unable to deserialize "+file, ex);
			if (in != null) {
				try { in.close(); }
				catch (Exception ignore) { }
			}
			return null;
		}
	}

	/**
	 * Deserialize an object from an InputStream.
	 * @param stream the source of the serialized object.
	 * @return the deserialized instance.
	 */
	public static Object deserialize(InputStream stream) {
		ObjectInputStream in = null;
		try {
			in = new ObjectInputStream(new BufferedInputStream(stream));
			Object object = in.readObject();
			in.close();
			return object;
		}
		catch (Exception ex) {
			logger.warn("Unable to obtain and deserialize the object", ex);
			if (in != null) {
				try { in.close(); }
				catch (Exception ignore) { }
			}
			return null;
		}
	}

	/**
	 * Serialize the supplied object in a specified file.
	 * @param file the directory in which to store the serialized object.
	 * @param object the instance to be serialized.
	 */
	public static void serialize(File file, Object object) {
		ObjectOutputStream out = null;
		try {
			out = new ObjectOutputStream(new FileOutputStream(file));
			out.writeObject(object);
			out.close();
		}
		catch(Exception ex) {
			logger.warn("Unable to serialize the object in "+file, ex);
			if (out != null) {
				try { out.close(); }
				catch (Exception ignore) { }
			}
		}
	}

	/**
	 * Serialize the supplied object to an OutputStream.
	 * @param stream the stream to which to write the serialized object.
	 * @param object the instance to be serialized.
	 */
	public static void serialize(OutputStream stream, Object object) {
		ObjectOutputStream out = null;
		try {
			out = new ObjectOutputStream(new BufferedOutputStream(stream));
			out.writeObject(object);
			out.close();
		}
		catch(Exception ex) {
			logger.warn("Unable to serialize the object", ex);
			if (out != null) {
				try { out.close(); }
				catch (Exception ignore) { }
			}
		}
	}

	/**
	 * Serialize the supplied object to a byte array.
	 * @param object the instance to be serialized.
	 */
	public static byte[] serialize(Object object) {
		ObjectOutputStream out = null;
		try {
			ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
			out = new ObjectOutputStream(new BufferedOutputStream(byteStream));
			out.writeObject(object);
			out.close();
			return byteStream.toByteArray();
		}
		catch(Exception ex) {
			logger.warn("Unable to serialize the object", ex);
			if (out != null) {
				try { out.close(); }
				catch (Exception ignore) { }
			}
			return new byte[0];
		}
	}

	/**
	 * Deserialize an object from a byte array.
	 * @param bytes the bytes of the serialized object.
	 * @return the deserialized instance.
	 */
	public static Object deserialize(byte[] bytes) {
		ObjectInputStream in = null;
		try {
			ByteArrayInputStream byteStream = new ByteArrayInputStream(bytes);
			in = new ObjectInputStream(new BufferedInputStream(byteStream));
			Object object = in.readObject();
			in.close();
			return object;
		}
		catch (Exception ex) {
			logger.warn("Unable deserialize the object", ex);
			if (in != null) {
				try { in.close(); }
				catch (Exception ignore) { }
			}
			return null;
		}
	}


}

