/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Encapsulates static methods for working with files and directories.
 */
public class FileUtil {

	public static Charset latin1 = Charset.forName("ISO-8859-1");
	public static Charset utf8 = Charset.forName("UTF-8");

	/**
	 * Read a file completely and return a byte array containing the data.
	 * @param file the file to read.
	 * @return the bytes of the file, or an empty array if an error occurred.
	 */
	public static byte[] getBytes(File file) {
		return getBytes(file, -1);
	}

	/**
	 * Read the first bytes of a file and return a byte array containing the data.
	 * @param file the file to read.
	 * @param length the number of bytes to read, or -1 if the entire file is to be read.
	 * @return the bytes, or an empty array if an error occurred.
	 */
	public static byte[] getBytes(File file, int length) {
		if (!file.exists()) return new byte[0];
		int fileLength = (int)file.length();
		if (length == -1) length = fileLength;
		if (fileLength < length) length = fileLength;
		byte[] bytes = new byte[length];
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(file);
			fis.read(bytes, 0, bytes.length);
			close(fis);
			return bytes;
		}
		catch (Exception e) { close(fis); return new byte[0]; }
	}

	/**
	 * Read an InputStream completely.
	 * @param stream the InputStream to read.
	 * @return the bytes, or an empty byte array if an error occurred.
	 */
	public static byte[] getBytes(InputStream stream) {
		BufferedInputStream bis = null;
		try {
			bis = new BufferedInputStream(stream);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			int n;
			byte[] buf = new byte[1024];
			while ((n=bis.read(buf, 0, buf.length)) != -1) baos.write(buf, 0, n);
			close(bis);
			return baos.toByteArray();
		}
		catch (Exception e) { close(bis); return new byte[0]; }
	}

	/**
	 * Read the first bytes of an InputStream and leave the stream open.
	 * @param stream the InputStream to read.
	 * @return the bytes, or an empty byte array if an error occurred.
	 * Note: the returned byte array may be shorter than the requested
	 * length if the input stream ends before the length has been reached.
	 */
	public static byte[] getBytes(InputStream stream, int length) {
		BufferedInputStream bis = null;
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			int n = 0;
			int bufSize = 1024;
			byte[] buf = new byte[bufSize];
			int remaining = length;
			while ((remaining > 0) && (n != -1)) {
				int toRead = Math.min(bufSize, remaining);
				n = stream.read(buf, 0, remaining);
				if (n != -1) {
					baos.write(buf, 0, n);
					remaining -= n;
				}
			}
			return baos.toByteArray();
		}
		catch (Exception e) { return new byte[0]; }
	}

	/**
	 * Read an InputStream completely, using the UTF-8 encoding.
	 * @param stream the InputStream to read.
	 * @return the text, or an empty string if an error occurred.
	 */
	public static String getText(InputStream stream) {
		return getText(stream, utf8);
	}

	/**
	 * Read an InputStream completely, using the specified encoding, or
	 * UTF-8 if the specified encoding is not supported.
	 * @param stream the InputStream to read.
	 * @param encoding the name of the charset to use.
	 * @return the text, or an empty string if an error occurred.
	 */
	public static String getText(InputStream stream, String encoding) {
		Charset charset;
		try { charset = Charset.forName(encoding); }
		catch (Exception ex) { charset = utf8; }
		return getText(stream, charset);
	}

	/**
	 * Read an InputStream completely, using the specified encoding.
	 * @param stream the InputStream to read.
	 * @param charset the character set to use for the encoding of the file.
	 * @return the text, or an empty string if an error occurred.
	 */
	public static String getText(InputStream stream, Charset charset) {
		BufferedReader br = null;
		try {
			br = new BufferedReader( new InputStreamReader(stream, charset) );
			StringWriter sw = new StringWriter();
			int n;
			char[] cbuf = new char[1024];
			while ((n=br.read(cbuf, 0, cbuf.length)) != -1) sw.write(cbuf,0,n);
			br.close();
			return sw.toString();
		}
		catch (Exception e) { close(br); return ""; }
	}

	/**
	 * Read a text file completely, using the UTF-8 encoding.
	 * @param file the file to read.
	 * @return the text of the file, or an empty string if an error occurred.
	 */
	public static String getText(File file) {
		return getText(file, utf8);
	}

	/**
	 * Read a text file completely, using the specified encoding, or
	 * UTF-8 if the specified encoding is not supported.
	 * @param file the file to read.
	 * @param encoding the name of the charset to use.
	 * @return the text of the file, or an empty string if an error occurred.
	 */
	public static String getText(File file, String encoding) {
		Charset charset;
		try { charset = Charset.forName(encoding); }
		catch (Exception ex) { charset = utf8; }
		return getText(file, charset);
	}

	/**
	 * Read a text file completely, using the specified encoding.
	 * @param file the file to read.
	 * @param charset the character set to use for the encoding of the file.
	 * @return the text of the file, or an empty string if an error occurred.
	 */
	public static String getText(File file, Charset charset) {
		BufferedReader br = null;
		try {
			if (!file.exists()) return "";
			br = new BufferedReader(
					new InputStreamReader(
						new FileInputStream(file), charset));
			StringWriter sw = new StringWriter();
			int n;
			char[] cbuf = new char[1024];
			while ((n=br.read(cbuf, 0, cbuf.length)) != -1) sw.write(cbuf,0,n);
			br.close();
			return sw.toString();
		}
		catch (Exception e) { close(br); return ""; }
	}

	/**
	 * Read a resource completely and return a byte array containing the data.
	 * This method uses the context classloader to find the resource.
	 * @param url the resource to read.
	 * @return the bytes of the resource, or an empty array if an error occurred.
	 */
	public static byte[] getBytes(URL url) {
		InputStream is = null;
		try {
			is = url.openStream();
			return getBytes(is);
		}
		catch (Exception e) { close(is); return new byte[0]; }
	}

	/**
	 * Read a resource completely, using the UTF-8 encoding.
	 * This method uses the context classloader to find the resource.
	 * @param url the resource to read.
	 * @return the text, or an empty string if an error occurred.
	 */
	public static String getText(URL url) {
		return getText(url, utf8);
	}

	/**
	 * Read a resource completely, using the specified encoding, or
	 * UTF-8 if the specified encoding is not supported.
	 * This method uses the context classloader to find the resource.
	 * @param url the resource to read.
	 * @param encoding the name of the charset to use.
	 * @return the text, or an empty string if an error occurred.
	 */
	public static String getText(URL url, String encoding) {
		Charset charset;
		try { charset = Charset.forName(encoding); }
		catch (Exception ex) { charset = utf8; }
		return getText(url, charset);
	}

	/**
	 * Read a resource completely, using the specified encoding.
	 * This method uses the context classloader to find the resource.
	 * @param url the resource to read.
	 * @param charset the character set to use for the encoding of the file.
	 * @return the text, or an empty string if an error occurred.
	 */
	public static String getText(URL url, Charset charset) {
		InputStream is = null;
		try {
			is = url.openStream();
			return getText(is, charset);
		}
		catch (Exception e) { close(is); return ""; }
	}

	/**
	 * Write a string to a text file using the UTF-8 encoding.
	 * @param file the file to write.
	 * @param text the string to write into the file.
	 * @return true if the operation succeeded completely; false otherwise.
	 */
	public static boolean setText(File file, String text) {
		return setText(file, utf8, text);
	}

	/**
	 * Write a string to a text file, using the specified encoding, or
	 * UTF-8 if the specified encoding is not supported.
	 * @param file the file to write.
	 * @param encoding the name of the charset to use.
	 * @param text the string to write into the file.
	 * @return true if the operation succeeded completely; false otherwise.
	 */
	public static boolean setText(File file, String encoding, String text) {
		Charset charset;
		try { charset = Charset.forName(encoding); }
		catch (Exception ex) { charset = utf8; }
		return setText(file, charset, text);
	}

	/**
	 * Write a string to a text file, using the specified encoding.
	 * @param file the file to write.
	 * @param charset the character set to use for the encoding of the file.
	 * @param text the string to write into the file.
	 * @return true if the operation succeeded completely; false otherwise.
	 */
	public static synchronized boolean setText(File file, Charset charset, String text) {
		BufferedWriter bw = null;
		try {
			bw = new BufferedWriter(
					new OutputStreamWriter(
						new FileOutputStream(file),charset));
			bw.write(text, 0, text.length());
			bw.flush();
			close(bw);
			return true;
		}
		catch (Exception e) { close(bw); return false; }
	}

	/**
	 * Copy a complete directory tree from one directory to another.
	 * @param inDir the directory to copy.
	 * @param outDir the copy.
	 * @return true if the operation succeeded completely; false otherwise.
	 */
	public static synchronized boolean copyDirectory(File inDir, File outDir) {
		outDir.mkdirs();
		File[] files = inDir.listFiles();
		for (File inFile : files) {
			File outFile = new File(outDir, inFile.getName());
			if (inFile.isFile()) {
				if (!copy(inFile, outFile)) return false;
			}
			else {
				if (!copyDirectory(inFile, outFile)) return false;
			}
		}
		return true;
	}

	/**
	 * Copy a file.
	 * @param inFile the file to copy.
	 * @param outFile the copy.
	 * @return true if the operation succeeded completely; false otherwise.
	 */
	public static synchronized boolean copy(File inFile, File outFile) {
		try { return copy(new FileInputStream(inFile), new FileOutputStream(outFile), -1); }
		catch (Exception e) { return false; }
	}

	/**
	 * Copy a maximum number of bytes from an InputStream to an OutputStream.
	 * The OutputStream is always closed when the operation is complete.
	 * If the contentLength is positive, the InputStream is left open; else it is closed
	 * when the operation is complete.
	 * @param in the stream to copy.
	 * @param out the copy.
	 * @param contentLength the maximum number of bytes to copy, or -1 to read the InputStream fully.
	 * @return true if the operation succeeded completely; false otherwise.
	 */
	public static synchronized boolean copy(InputStream in, OutputStream out, int contentLength) {
		int length = (contentLength > 0) ? contentLength : Integer.MAX_VALUE;
		boolean result = true;
		int bytesRead = 0;
		try {
			byte[] b = new byte[4096];
			int n;
			while ( (bytesRead < length) &&
						((n = in.read(b, 0, Math.min(b.length, length-bytesRead))) != -1) ) {
				out.write(b, 0, n);
				bytesRead += n;
			}
			out.flush();
		}
		catch (Exception ex) { result = false; }
		finally {
			if (contentLength < 0) close(in);
			close(out);
		}
		return result;
	}

	/**
	 * Read and discard a specified number of bytes from an InputStream.
	 * The InputStream is not closed when the operation is complete.
	 * @param in the stream to read.
	 * @param contentLength the number of bytes to read and discard,
	 * or -1 to read the InputStream fully.
	 * @return true if the operation succeeded completely; false otherwise.
	 */
	public static synchronized boolean discard(InputStream in, int contentLength) {
		int length = (contentLength > 0) ? contentLength : Integer.MAX_VALUE;
		boolean result = true;
		int bytesRead = 0;
		try {
			byte[] b = new byte[4096];
			int n;
			while ( (bytesRead < length) &&
						((n = in.read(b, 0, Math.min(b.length, length-bytesRead))) != -1) ) {
				bytesRead += n;
			}
		}
		catch (Exception ex) { result = false; }
		return result;
	}

	/**
	 * Close an InputStream and ignore Exceptions.
	 * @param stream the stream to close.
	 */
	public static void close(InputStream stream) {
		if (stream != null) {
			try { stream.close(); }
			catch (Exception ignore) { }
		}
	}

	/**
	 * Close an OutputStream and ignore Exceptions.
	 * @param stream the stream to close.
	 */
	public static void close(OutputStream stream) {
		if (stream != null) {
			try { stream.close(); }
			catch (Exception ignore) { }
		}
	}

	/**
	 * Close a Writer and ignore Exceptions.
	 * @param writer the writer to close.
	 */
	public static void close(Writer writer) {
		if (writer != null) {
			try { writer.close(); }
			catch (Exception ignore) { }
		}
	}

	/**
	 * Close a Reader and ignore Exceptions.
	 * @param reader the reader to close.
	 */
	public static void close(Reader reader) {
		if (reader != null) {
			try { reader.close(); }
			catch (Exception ignore) { }
		}
	}

	/**
	 * Close a JarFile and ignore Exceptions.
	 * @param jarFile the JarFile to close.
	 */
	public static void close(JarFile jarFile) {
		if (jarFile != null) {
			try { jarFile.close(); }
			catch (Exception ignore) { }
		}
	}

	/**
	 * Close a ZipFile and ignore Exceptions.
	 * @param zipFile the zipFile to close.
	 */
	public static void close(ZipFile zipFile) {
		if (zipFile != null) {
			try { zipFile.close(); }
			catch (Exception ignore) { }
		}
	}

	/**
	 * Get a File for a name, copying a default file into place
	 * if the File does not exist.
	 * @param fileName the file to create, if necessary.
	 * @param exampleName the file to copy, if necessary.
	 * @return a File pointing to the file, or null if the file could not be created.
	 */
	public static File getFile(String fileName, String exampleName) {
		File file = null;
		if ((fileName != null) && !(fileName = fileName.trim()).equals("")) {
			file = new File(fileName);
			if (!file.exists()) {
				if ((exampleName != null) && !(exampleName = exampleName.trim()).equals("")) {
					File example = new File(exampleName);
					if (example.exists()) {
						createParentDirectory(file);
						return (copy(example, file) ? file : null);
					}
				}
				return null;
			}
		}
		return file;
	}

	/**
	 * Get a File, copying a default file into place if the file does not exist.
	 * This method never overwrites the destination file.
	 * @param file the file to create, if necessary.
	 * @param defaultFile the file to copy, if necessary.
	 * @return a File pointing to the file, or null if the file could not be created.
	 */
	public static File getFile(File file, File defaultFile) {
		if ((file != null) && !file.exists()) {
			if (defaultFile == null) return null;
			if (defaultFile.exists()) {
				createParentDirectory(file);
				if (!copy(defaultFile, file)) return null;
			}
		}
		return file;
	}

	/**
	 * Get a File, copying a resource into place if the file does not exist.
	 * This method never overwrites the destination file.
	 * @param file the file to create, if necessary.
	 * @param resourcePath the resource to copy, if necessary.
	 * @return a File pointing to the file, or null if the file could not be created.
	 */
	public static File getFile(File file, String resourcePath) {
		if ((file != null) && !file.exists()) {
			if (resourcePath == null) return null;
			if (!resourcePath.startsWith("/")) resourcePath = "/" + resourcePath;
			try {
				createParentDirectory(file);
				InputStream in = FileUtil.class.getResourceAsStream(resourcePath);
				if (in == null) return null;
				FileOutputStream out = new FileOutputStream(file);
				if (!copy(in, out, -1)) return null;
			}
			catch (Exception ex) { }
		}
		return file;
	}

	/**
	 * Get an InputStream for a resource.
	 * @param resourcePath the file to get if the primaryFile doesn't exist.
	 * @return the stream for the resource, or null if the stream could not be created.
	 */
	public static InputStream getStream(String resourcePath) {
		try {
			//If there is a cache, then copy the resource into the cache
			//and return a FileInputStream pointing to the file in the cache.
			Cache cache = Cache.getInstance();
			if (cache != null) {
				File file = cache.getFile(resourcePath);
				return new FileInputStream(file);
			}
			//If there is no cache, then serve the resource from the classpath.
			if (!resourcePath.startsWith("/")) resourcePath = "/" + resourcePath;
			return FileUtil.class.getResourceAsStream(resourcePath);
		}
		catch (Exception ex) { }
		return null;
	}

	/**
	 * Get an InputStream for a file if it exists or a default file if the primary
	 * file doesn't exist. This method does not copy the default file to the primary
	 * file if the primary file does not exist.
	 * @param primaryFile the file to get if it exists.
	 * @param defaultFile the file to get if the primaryFile doesn't exist.
	 * @return the stream for the selected file, or null if the stream could not be created.
	 */
	public static InputStream getStream(File primaryFile, File defaultFile) {
		try {
			if ((primaryFile != null) && primaryFile.exists()) {
				return new FileInputStream(primaryFile);
			}
			else if ((defaultFile != null) && defaultFile.exists()) {
				return new FileInputStream(defaultFile);
			}
		}
		catch (Exception ex) { }
		return null;
	}

	/**
	 * Get an InputStream for a file if it exists or a resource if the primary
	 * file doesn't exist. This method does not copy the resource to the primary
	 * file if the primary file does not exist.
	 * @param primaryFile the file to get if it exists.
	 * @param resourcePath the file to get if the primaryFile doesn't exist.
	 * @return the stream for the selected file, or null if the stream could not be created.
	 */
	public static InputStream getStream(File primaryFile, String resourcePath) {
		try {
			//If the primaryFile exists use it
			if ((primaryFile != null) && primaryFile.exists()) {
				return new FileInputStream(primaryFile);
			}
			else if (resourcePath != null) {
				//The primaryFile didn't exist, but there is a resourcePath.
				//In this case, copy the resource to the Cache if possible,
				//so it can be served from there in the future.
				return getStream(resourcePath);
			}
		}
		catch (Exception ex) { }
		return null;
	}

	/**
	 * Get a URL from a path, returning null if an error occurs.
	 * @param path the resource path (if the path does not start with a slash, one is inserted).
	 * @return the URL corresponding to the path, or null if the URL could not be created.
	 */
	public static URL getURL(String path) {
		if (!path.startsWith("/")) path = "/" + path;
		URL url = (new FileUtil()).getClass().getResource(path);
		return url;
	}

	/**
	 * Ensure that the parent directory of a file exists, creating it, and
	 * all its ancestors, if necessary.
	 * @param file the file whose parent is to be created if necessary.
	 * @return a File pointing to the parent directory of the file
	 */
	public static File createParentDirectory(File file) {
		File parent = new File(file.getAbsolutePath()).getParentFile();
		parent.mkdirs();
		return parent;
	}

	/**
	 * Create a temp directory in a specified parent.
	 * @param parent the parent directory.
	 * @return a File pointing to the temp directory.
	 */
	public static File createTempDirectory(File parent) {
		parent = new File( parent.getAbsolutePath() );
		parent.mkdirs();
		try {
			File temp = File.createTempFile("TMP-", "", parent);
			temp.delete();
			temp.mkdirs();
			return temp;
		}
		catch (Exception useParent) { return parent; }
	}

	/**
	 * Delete a file or a directory. If the file is a directory, delete
	 * the contents of the directory and all its child directories, then
	 * delete the directory itself. If the supplied file is null, nothing
	 * is done and true is returned.
	 * @param file the file to delete.
	 * @return true if the operation succeeded completely; false otherwise.
	 */
	public static boolean deleteAll(File file) {
		boolean b = true;
		if ((file != null) && file.exists()) {
			if (file.isDirectory()) {
				try {
					File[] files = file.listFiles();
					for (File f : files) b &= deleteAll(f);
				}
				catch (Exception e) { return false; }
			}
			b &= file.delete();
		}
		return b;
	}

	/**
	 * Zip a directory and its subdirectories, preserving the name of
	 * the root directory (dir) in all paths in the zip file.
	 * @param dir the directory to zip.
	 * @param zipFile the output zip file.
	 * @return true if the operation succeeded completely; false otherwise.
	 */
	public static synchronized boolean zipDirectory(File dir, File zipFile) {
		return zipDirectory(dir, zipFile, false);
	}

	/**
	 * Zip a directory and its subdirectories.
	 * @param dir the directory to zip.
	 * @param zipFile the output zip file.
	 * @param suppressRoot true if the path to the root directory (dir) is
	 * to be suppressed; false if all paths in the zip file are to start
	 * with the name of the root directory.
	 * @return true if the operation succeeded completely; false otherwise.
	 */
	public static synchronized boolean zipDirectory(File dir, File zipFile, boolean suppressRoot) {
		try {
			dir = dir.getCanonicalFile();

			int rootLength;
			if (suppressRoot) {
				//Get the length of the dir path
				rootLength = dir.getAbsolutePath().length();
			}
			else {
				//Get the length of the parent path
				File parent = dir.getParentFile();
				rootLength = parent.getAbsolutePath().length();
			}
			rootLength++; //allow for the slash that will appear in files that are zipped

			//Get the streams
			FileOutputStream fout = new FileOutputStream(zipFile);
			ZipOutputStream zout = new ZipOutputStream(fout);

			zipDirectory(zout, dir, rootLength);
			zout.close();
			return true;
		}
		catch (Exception ex) { return false; }
	}

	//Zip a directory and its subdirectories into a ZipOutputStream,
	//setting the root of the zip package to be the parent directory
	//of the originally requested directory.
	private static synchronized void zipDirectory(ZipOutputStream zout, File dir, int rootLength)
												throws Exception {
		if (dir.isDirectory()) {
			String name = dir.getAbsolutePath() + "/";
			if (name.length() > rootLength) {
				name = name.substring(rootLength);
				if (!name.endsWith("/")) name += "/";
				ZipEntry ze = new ZipEntry(name);
				zout.putNextEntry(ze);
			}
			File[] files = dir.listFiles();
			for (File file : files) {
				if (file.isDirectory()) zipDirectory(zout, file, rootLength);
				else zipFile(zout, file, rootLength);
			}
		}
	}

	/**
	 * Zip an array of files from a single directory.
	 * @param list the filenames to include from the directory.
	 * @param dir the directory containing the files.
	 * @param zipFile the output zip file.
	 * @return true if the operation succeeded completely; false otherwise.
	 */
	public static boolean zipFiles(String[] list, File dir, File zipFile) {
		try {
			//Get the parent and find out how long it is
			File parent = dir.getParentFile();
			int rootLength = parent.getAbsolutePath().length() + 1;

			//Get the various streams and buffers
			FileOutputStream fout = new FileOutputStream(zipFile);
			ZipOutputStream zout = new ZipOutputStream(fout);

			for (String name : list) {
				File file = new File(dir, name);
				zipFile(zout, file, rootLength);
			}
			zout.close();
			return true;
		}
		catch (Exception ex) { return false; }
	}

	//Zip a file into a ZipOutputStream, setting the
	//root of the zip package to be the parent directory
	//of the originally requested directory.
	private static synchronized void zipFile(ZipOutputStream zout, File file, int rootLength)
												throws Exception {
		FileInputStream fin;
		ZipEntry ze;
		byte[] buffer = new byte[10000];
		int bytesread;
		String entryname = file.getAbsolutePath().substring(rootLength);
		entryname = entryname.replaceAll("\\\\", "/");
		ze = new ZipEntry(entryname);
		if (file.exists()) {
			fin = new FileInputStream(file);
			zout.putNextEntry(ze);
			while ((bytesread = fin.read(buffer)) > 0) zout.write(buffer,0,bytesread);
			zout.closeEntry();
			fin.close();
		}
	}

	/**
	 * Unpack a zip file into a root directory, preserving the directory structure of the zip file.
	 * @param root the directory into which to unpack the zip file.
	 * @param file the zip file to unpack.
	 * @param filterNames true if characters that would cause a problem
	 * in URLs are to be replaced with underscores; false if file names
	 * are to be preserved.
	 * @throws Exception if anything goes wrong.
	 */
	public static void unpackZipFile(File root, File file, boolean filterNames) throws Exception {
		if (!file.exists()) throw new Exception("Zip file does not exist ("+file+")");
		ZipFile zipFile = new ZipFile(file);
		Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
		while (zipEntries.hasMoreElements()) {
			ZipEntry entry = zipEntries.nextElement();
			String name = entry.getName().replace('/', File.separatorChar);

			//Make sure that the directory is present
			File outFile = new File(root, name);
			outFile.getParentFile().mkdirs();

			if (!entry.isDirectory()) {
				//Clean up any file names that might cause a problem in a URL.
				name = outFile.getName();
				if (filterNames) {
					name = name.trim()
								.replaceAll("[\\s]+","_")
								 .replaceAll("[*\"&'><#;:@/?=]","_");
				}
				outFile = new File(outFile.getParentFile(), name);

				//Now write the file with the corrected name.
				OutputStream out = new FileOutputStream(outFile);
				InputStream in = zipFile.getInputStream(entry);
				FileUtil.copy( in, out, -1 );
			}
		}
		FileUtil.close(zipFile);
	}

	/**
	 * Send a file to an output stream, closing the streams when done.
	 * @param file the file to stream.
	 * @param out the output stream.
	 * @throws Exception if any error occurs.
	 */
	public static void streamFile(File file, OutputStream out) throws Exception {
		FileInputStream fis = new FileInputStream(file);
		byte[] bbuf = new byte[1024];
		int n;
		while ((n=fis.read(bbuf,0,bbuf.length)) > 0) out.write(bbuf,0,n);
		out.flush();
		out.close();
		fis.close();
	}

	/**
	 * Send a file to an output stream, zipping it during the transmission and
	 * closing the output stream when done.
	 * @param file the file to stream.
	 * @param out the output stream.
	 * @throws Exception if any error occurs.
	 */
	public static void zipStreamFile(File file, OutputStream out) throws Exception {
		FileInputStream fin;
		ZipOutputStream zout = new ZipOutputStream(out);
		ZipEntry ze;
		byte[] buffer = new byte[10000];
		int bytesread;
		String entryname = file.getName();
		ze = new ZipEntry(entryname);
		if (file.exists()) {
			fin = new FileInputStream(file);
			zout.putNextEntry(ze);
			while ((bytesread = fin.read(buffer)) > 0) zout.write(buffer,0,bytesread);
			fin.close();
		}
		zout.closeEntry();
		zout.flush();
		zout.close();
	}

	/**
	 * Get an array of files from a directory and sort it by last-modified-date,
	 * in reverse chronological order. This method ignores child directories.
	 * @param dir the directory.
	 * @return the list of files in the directory, sorted by date last modified.
	 */
	public static File[] listSortedFiles(File dir) {
		if (!dir.isDirectory()) return new File[0];
		File[] files = dir.listFiles(
			new FileFilter() {
				public boolean accept(File file) {
					return file.isFile() && file.canRead() && file.canWrite();
				}
			}
		);
		Arrays.sort(
			files,
			new Comparator<File>() {
				public int compare(File f1, File f2) {
					//compute the difference in reverse order
					long diff = f1.lastModified() - f2.lastModified();
					return (diff > 0) ? 1 : ((diff < 0) ? -1 : 0);
				}
			}
		);
		return files;
	}

	/**
	 * Find the oldest file in a directory.
	 * @param dir the directory.
	 * @return the oldest file in the directory, or null if dir is not a directory.
	 */
	public static File findOldestFile(File dir) {
		if (!dir.exists()) return null;
		if (!dir.isDirectory()) return null;
		File[] files = dir.listFiles(
			new FileFilter() {
				public boolean accept(File file) {
					return file.isFile() && file.canRead() && file.canWrite();
				}
			}
		);
		if (files.length == 0) return null;
		File oldest = files[0];
		for (int i=1; i<files.length; i++) {
			if (files[i].lastModified() < oldest.lastModified()) {
				oldest = files[i];
			}
		}
		return oldest;
	}

	/**
	 * Get the number of files in a directory and its subdirectories.
	 * This method does not count directories as files.
	 * @param dir the directory.
	 * @return the total number of files in a directory and all
	 * its subdirectories, or one if the supplied File exists
	 * and is not a directory, or zero if the supplied File
	 * does not exist.
	 */
	public static int getFileCount(File dir) {
		if (!dir.exists()) return 0;
		if (!dir.isDirectory()) return 1;
		File[] files = dir.listFiles();
		int count = 0;
		for (int i=0; i<files.length; i++) {
			count += files[i].isFile() ? 1 : getFileCount(files[i]);
		}
		return count;
	}

}