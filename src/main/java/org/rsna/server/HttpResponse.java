/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.server;

import org.apache.log4j.Logger;
import org.rsna.util.FileUtil;
import org.rsna.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A simple HTTP text response in UTF-8.
 */
public class HttpResponse {

	static final Logger logger = Logger.getLogger(HttpResponse.class);
	static Hashtable<String,String> contentTypes = new ContentTypes();
	public static final int ok 				= 200;
	public static final int found			= 302;
	public static final int notmodified		= 304;
	public static final int unauthorized 	= 401;
	public static final int forbidden 		= 403;
	public static final int notfound 		= 404;
	public static final int servererror		= 500;
	public static final int notimplemented	= 501;

	protected static SimpleDateFormat dateFormat = null;

	final Socket socket;
	final OutputStream outputStream;
	final Hashtable<String,String> headers;
	final List<ResponseItem> responseContent;
	long responseLength = 0;
	int responseCode = 200;

	/**
	 * Create an HttpResponse, connecting it to an OutputStream and
	 * setting a default response code of 200.
	 * @param socket the socket on which to construct the response.
	 */
	public HttpResponse(Socket socket) throws Exception {
		this.socket = socket;
		outputStream = socket.getOutputStream();
		headers = new Hashtable<String,String> ();
		responseContent = new LinkedList<ResponseItem>();
		responseLength = 0;
		String date = getHttpDate(-1);
		if (date != null) setHeader( "Date", date );
	}

	/**
	 * Get the OutputStream associated with this response.
	 * @return the OutputStream.
	 */
	public OutputStream getOutputStream() {
		return outputStream;
	}

	/**
	 * Flush and close the OutputStream associated with this response.
	 */
	public synchronized void close() {
		try {
			if (outputStream != null) {
				outputStream.flush();
				outputStream.close();
			}
		}
		catch (Exception ignore) { logger.warn("Unable to close the output stream"); }
	}

	/**
	 * Set a response code for the HttpResponse.
	 * @param responseCode the integer response code to be sent with the response, e.g., 404
	 */
	public void setResponseCode(int responseCode) {
		this.responseCode = responseCode;
	}

	/**
	 * Insert a header.
	 * @param name the header name, e.g. "Content-Type".
	 * @param value the header text value, e.g., "application/zip".
	 */
	public void setHeader(String name, String value) {
		headers.put(name,value);
	}

	/**
	 * Set headers that disable caching.
	 */
	public void disableCaching() {
		String date = getHttpDate(-1);
		if (date != null) headers.put("Expires", date);
		headers.put("Pragma","no-cache");
		headers.put("Cache-Control","no-cache");
	}

	/**
	 * Set the Content-Type header that corresponds to the
	 * extension of a file. This method calls setContentType(String),
	 * supplying the extension of the file.
	 * @param file the file whose extension is to be used to determine
	 * the Content-Type.
	 * @return the Content-Type.
	 */
	public String setContentType(File file) {
		String name = file.getName();
		String ext = name.substring(name.lastIndexOf(".")+1);
		return setContentType(ext);
	}

	/**
	 * Set the Content-Type header that corresponds to a String.
	 * If the string doesn't correspond to a known Content-Type,
	 * the header is not set.
	 * <br><br>
	 * <table border="1">
	 *	<tr><td>application</td><td>application/x-ms-application</td></tr>
	 *	<tr><td>avi</td><td>video/x-msvideo</td></tr>
	 *	<tr><td>css</td><td>text/css;charset=UTF-8</td></tr>
	 *	<tr><td>csv</td><td>text/csv;charset=UTF-8</td></tr>
	 *	<tr><td>dcm</td><td>application/dicom</td></tr>
	 *	<tr><td>deploy</td><td>application/octet-stream</td></tr>
	 *	<tr><td>docx</td><td>application/vnd.openxmlformats-officedocument.wordprocessingml.document</td></tr>
	 *	<tr><td>dotx</td><td>application/vnd.openxmlformats-officedocument.wordprocessingml.template</td></tr>
	 *	<tr><td>gif</td><td>image/gif</td></tr>
	 *	<tr><td>htm</td><td>text/html;charset=UTF-8</td></tr>
	 *	<tr><td>html</td><td>text/html;charset=UTF-8</td></tr>
	 *	<tr><td>jar</td><td>application/java-archive</td></tr>
	 *	<tr><td>jnlp</td><td>application/x-java-jnlp-file;charset=UTF-8</td></tr>
	 *	<tr><td>jpeg</td><td>image/jpeg</td></tr>
	 *	<tr><td>jpg</td><td>image/jpeg</td></tr>
	 *	<tr><td>js</td><td>text/javascript;charset=UTF-8</td></tr>
	 *	<tr><td>manifest</td><td>application/x-ms-manifest</td></tr>
	 *	<tr><td>md</td><td>application/unknown</td></tr>
	 *	<tr><td>mp4</td><td>video/mp4</td></tr>
	 *	<tr><td>mpeg</td><td>video/mpeg</td></tr>
	 *	<tr><td>mpg</td><td>video/mpg</td></tr>
	 *	<tr><td>oga</td><td>audio/oga</td></tr>
	 *	<tr><td>ogg</td><td>video/ogg</td></tr>
	 *	<tr><td>ogv</td><td>video/ogg</td></tr>
	 *	<tr><td>pdf</td><td>application/pdf</td></tr>
	 *	<tr><td>png</td><td>image/png</td></tr>
	 *	<tr><td>pot</td><td>application/vnd.ms-powerpoint</td></tr>
	 *	<tr><td>potx</td><td>application/vnd.openxmlformats-officedocument.presentationml.template</td></tr>
	 *	<tr><td>pps</td><td>application/vnd.ms-powerpoint</td></tr>
	 *	<tr><td>ppsx</td><td>application/vnd.openxmlformats-officedocument.presentationml.slideshow</td></tr>
	 *	<tr><td>ppt</td><td>application/vnd.ms-powerpoint</td></tr>
	 *	<tr><td>pptx</td><td>application/vnd.openxmlformats-officedocument.presentationml.presentation</td></tr>
	 *	<tr><td>svg</td><td>image/xvg+xml</td></tr>
	 *	<tr><td>swf</td><td>application/x-shockwave-flash</td></tr>
	 *	<tr><td>txt</td><td>text/plain;charset=UTF-8</td></tr>
	 *	<tr><td>wav</td><td>audio/wav</td></tr>
	 *	<tr><td>webm</td><td>video/webm</td></tr>
	 *	<tr><td>wmv</td><td>video/x-ms-wmv</td></tr>
	 *	<tr><td>xlsx</td><td>application/vnd.openxmlformats-officedocument.spreadsheetml.sheet</td></tr>
	 *	<tr><td>xltx</td><td>application/vnd.openxmlformats-officedocument.spreadsheetml.template</td></tr>
	 *	<tr><td>xml</td><td>text/xml;charset=UTF-8</td></tr>
	 *	<tr><td>zip</td><td>application/zip</td></tr>
	 * </table>
	 * @param ext the name of a Content-Type (e.g. "html", "xml", etc.).
	 * @return the Content-Type.
	 */
	public String setContentType(String ext) {
		String contentType = contentTypes.get(ext);
		if (contentType != null) setHeader("Content-Type", contentType);
		return contentType;
	}

	/**
	 * Set the Disposition header for a file.
	 * @param file the file whose name is to be inserted into
	 * the Disposition header.
	 */
	public String setContentDisposition(File file) {
		String disposition = "attachment; filename=\"" + file.getName() + "\"";
		setHeader("Content-Disposition", disposition);
		return disposition;
	}

	/**
	 * Set the Last-Modified header for a file.
	 * @param time the last modified date in milliseconds.
	 */
	public void setLastModified(long time) {
		String date = getHttpDate(time);
		if (date != null) setHeader("Last-Modified", date);
	}

	/**
	 * Set the ETag header using the value of a long integer.
	 * @param value the value to be used as the ETag.
	 */
	public void setETag(long value) {
		setHeader("ETag", "\""+value+"\"");
	}

	/**
	 * Convert a millisecond time to the Http date format for use in headers.
	 * The format returned is: "Thu, 16 Mar 2000 11:00:00 GMT"
	 * @param time Date value, or -1 to use the current datetime.
	 */
	public synchronized String getHttpDate(long time) {
		if (dateFormat == null) {
			dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
			dateFormat.setTimeZone( TimeZone.getTimeZone("GMT") );
		}
		Date date = (time == -1) ? new Date() : new Date(time);
		try { return dateFormat.format(date); }
		catch (Exception ex) { return null; }
	}

	/**
	 * Add a text string content item to the response.
	 * @param string the string to be added to the response.
	 */
	public void write(String string) {
		try {
			ResponseItem item = new ResponseItem(string);
			responseContent.add(item);
			responseLength += item.length;
		}
		catch (Exception ignore) {
			logger.warn("Could not add \""+string+"\" to the response.");
		}
	}

	/**
	 * Add a file content item to the response.
	 * @param file the file whose contents are to be added to the response.
	 */
	public void write(File file) {
		try {
			ResponseItem item = new ResponseItem(file);
			responseContent.add(item);
			responseLength += item.length;
		}
		catch (Exception ignore) {
			logger.warn("Unable to add file "+file+" to the response.");
		}
	}

	/**
	 * Add a byte array content item to the response.
	 * @param bytes to be added to the response.
	 */
	public void write(byte[] bytes) {
		try {
			ResponseItem item = new ResponseItem(bytes);
			responseContent.add(item);
			responseLength += item.length;
		}
		catch (Exception ignore) {
			logger.warn("Unable to add the byte array to the response.");
		}
	}

	/**
	 * Add a resource content item to the response.
	 * @param url the URL of the resource to be added to the response.
	 */
	public void write(URL url) {
		try {
			byte[] bytes = FileUtil.getBytes( url.openStream() );
			ResponseItem item = new ResponseItem(bytes);
			responseContent.add(item);
			responseLength += item.length;
		}
		catch (Exception ignore) {
			logger.warn("Unable to add the resource "+url+" to the response.");
		}
	}

	/**
	 * Set the HttpResponse to trigger a redirect. This method sets
	 * the HTTP response code to 302, adds the Location header,
	 * <b>and</b> calls the send() method.
	 * @param url the destination
	 */
	public void redirect(String url) {
		setResponseCode(found);
		setHeader("Location", url);
		send();
	}

	/**
	 * Send the response, setting the Content-Length header
	 * and including all the content items.
	 */
	public boolean send() {
		try {
			String preamble =
				"HTTP/1.1 " + responseCode + "\r\n" +
				getHeadersString() +
				"Content-Length: " + responseLength + "\r\n\r\n";
			byte[] preambleBytes = preamble.getBytes("UTF-8");
			outputStream.write(preambleBytes);
			ListIterator<ResponseItem> it = responseContent.listIterator();
			while (it.hasNext()) it.next().write();
			outputStream.flush();
			return true;
		}
		catch (Exception ex) {
			//logger.error("Unable to send the response.", ex);
			return false;
		}
	}

	//Get all the headers as a string.
	String getHeadersString() {
		StringBuffer sb = new StringBuffer();
		Enumeration<String> keys = headers.keys();
		while (keys.hasMoreElements()) {
			String key = keys.nextElement();
			String value = headers.get(key);
			sb.append(key + ": " + value + "\r\n");
		}
		return sb.toString();
	}

	/**
	 * List the headers for this HttpResponse
	 */
	public String listHeaders(String margin) {
		StringBuffer sb = new StringBuffer();
		for (String key : headers.keySet()) {
			sb.append(margin + key + ": " + headers.get(key) + "\n");
		}
		if (sb.length() == 0) sb.append(margin + "none\n");
		return sb.toString();
	}

	//A class to encapsulate one part of the content of a response.
	class ResponseItem {

		byte[] bytes = null;
		File file = null;
		long length = 0;

		public ResponseItem(byte[] bytes) throws Exception {
			this.bytes = bytes;
			length = bytes.length;
		}

		public ResponseItem(String string) throws Exception {
			bytes = string.getBytes("UTF-8");
			length = bytes.length;
		}

		public ResponseItem(File file) throws Exception {
			this.file = file;
			length = file.length();
		}

		public void write() {
			try {
				if (bytes != null) outputStream.write(bytes);
				else if (file != null) {
					FileInputStream inputStream = new FileInputStream(file);
					int nbytes;
					byte[] buffer = new byte[2048];
					while ((nbytes = inputStream.read(buffer)) != -1) {
						outputStream.write(buffer,0,nbytes);
					}
					inputStream.close();
				}
			}
			catch (Exception ignore) {
				logger.debug("Unable to write to the output stream.", ignore);
			}
		}
	}

	//A static class to provide a mapping from file extension to Content-Type.
	static class ContentTypes extends Hashtable<String,String> {
		public ContentTypes() {
			super();
			put("application", "application/x-ms-application");
			put("avi","video/x-msvideo");
			put("css","text/css;charset=UTF-8");
			put("csv","text/csv;charset=UTF-8");
			put("dcm","application/dicom");
			put("deploy","application/octet-stream");
			put("docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document");
			put("dotx","application/vnd.openxmlformats-officedocument.wordprocessingml.template");
			put("gif","image/gif");
			put("htm","text/html;charset=UTF-8");
			put("html","text/html;charset=UTF-8");
			put("jar","application/java-archive");
			put("jnlp","application/x-java-jnlp-file;charset=UTF-8");
			put("jpeg","image/jpeg");
			put("jpg","image/jpeg");
			put("js","text/javascript;charset=UTF-8");
			put("manifest", "application/x-ms-manifest");
			put("md","application/unknown");
			put("mp4","video/mp4");
			put("mpeg","video/mpg");
			put("mpg","video/mpg");
			put("oga","audio/oga");
			put("ogg","video/ogg");
			put("ogv","video/ogg");
			put("pdf","application/pdf");
			put("png","image/png");
			put("pot","application/vnd.ms-powerpoint");
			put("potx","application/vnd.openxmlformats-officedocument.presentationml.template");
			put("pps","application/vnd.ms-powerpoint");
			put("ppsx","application/vnd.openxmlformats-officedocument.presentationml.slideshow");
			put("ppt","application/vnd.ms-powerpoint");
			put("pptx","application/vnd.openxmlformats-officedocument.presentationml.presentation");
			put("svg","image/svg+xml");
			put("swf","application/x-shockwave-flash");
			put("txt","text/plain;charset=UTF-8");
			put("wav","audio/wav");
			put("webm","video/webm");
			put("wmv","video/x-ms-wmv");
			put("xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
			put("xltx","application/vnd.openxmlformats-officedocument.spreadsheetml.template");
			put("xml","text/xml;charset=UTF-8");
			put("zip","application/zip");

			File file = new File("ContentTypes.xml");
			if (file.exists()) {
				try {
					Document doc = XmlUtil.getDocument(file);
					Element root = doc.getDocumentElement();
					Node child = root.getFirstChild();
					while (child != null) {
						if ((child instanceof Element) && child.getNodeName().toLowerCase().equals("file")) {
							Element filetype = (Element)child;
							String ext = filetype.getAttribute("ext").toLowerCase();
							if (ext.startsWith(".")) ext = ext.substring(1);
							String type = filetype.getAttribute("type");
							put(ext, type);
						}
						child = child.getNextSibling();
					}
				}
				catch (Exception skip) {
					logger.warn("Unable to load ContentType extensions ("+file+")");
				}
			}
		}
	}
}


