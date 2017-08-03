// Copyright (C) 1998-2001 by Jason Hunter <jhunter_AT_acm_DOT_org>.
// All rights reserved.  Use of this class is limited.
// Please see the LICENSE for more information.

package org.rsna.multipart; //repackage to RSNA util

import java.io.IOException;
import java.util.Enumeration;
import java.util.Vector;

/**
 * A utility class to handle <code>multipart/form-data</code> requests.
 */
public class MultipartParser {

	/** input stream to read parts from */
	private MultipartInputStream in;

	/** MIME boundary that delimits parts */
	private String boundary;

	/** reference to the last file part we returned */
	private FilePart lastFilePart;

	/** buffer for readLine method */
	private byte[] buf = new byte[8 * 1024];

	/** preferred encoding */
	private String encoding = "UTF-8";

	/**
	 * Creates a <code>MultipartParser</code> from the specified request,
	 * which limits the upload size to the specified length, buffers for
	 * performance and prevent attempts to read past the amount specified
	 * by the Content-Length.
	 *
	 * @param type the content type of the POST.
	 * @param in the input stream of the request.
	 */
	public MultipartParser(String type, MultipartInputStream in) throws IOException {

		// Get the boundary string; it's included in the content type.
		// Should look something like "------------------------12012133613061"
		String boundary = extractBoundary(type);
		if (boundary == null) {
			throw new IOException("Separation boundary was not specified");
		}

		// Save our values for later
		this.in = in;
		this.boundary = boundary;

		// Read until we hit the boundary
		// Some clients send a preamble (per RFC 2046), so ignore that
		// Thanks to Ben Johnson, ben.johnson@merrillcorp.com, for pointing out
		// the need for preamble support.
		do {
			String line = readLine();
			if (line == null) {
				throw new IOException("Corrupt form data: premature ending");
			}
			// See if this line is the boundary, and if so break
			if (line.startsWith(boundary)) {
				break;  // success
			}
		} while (true);
  }

	/**
	 * Read the next part arriving in the stream. Will be either a
	 * <code>FilePart</code> or a <code>ParamPart</code>, or <code>null</code>
	 * to indicate there are no more parts to read. The order of arrival
	 * corresponds to the order of the form elements in the submitted form.
	 *
	 * @return either a <code>FilePart</code>, a <code>ParamPart</code> or
	 *        <code>null</code> if there are no more parts to read.
	 * @exception IOException	if an input or output exception has occurred.
	 *
	 * @see FilePart
	 * @see ParamPart
	 */
	public Part readNextPart() throws IOException {

		// Make sure the last file was entirely read from the input
		if (lastFilePart != null) {
			lastFilePart.getInputStream().close();
			lastFilePart = null;
		}

		// Read the headers; they look like this (not all may be present):
		// Content-Disposition: form-data; name="field1"; filename="file1.txt"
		// Content-Type: type/subtype
		// Content-Transfer-Encoding: binary
		Vector<String> headers = new Vector<String>();

		String line = readLine();
		if ((line == null) || (line.length() == 0)) {
			return null; // No parts left, we're done
		}

		// Read the following header lines until we hit an empty line.
		// A line starting with whitespace is considered a continuation;
		// that requires a little special logic.  Thanks to Nic Ferrier for
		// identifying a good fix.
		while (line != null && line.length() > 0) {
			String nextLine = null;
			boolean getNextLine = true;
			while (getNextLine) {
				nextLine = readLine();
				if ( (nextLine != null) && (nextLine.startsWith(" ") || nextLine.startsWith("\t")) ) {
					line = line + nextLine;
				}
				else {
					getNextLine = false;
				}
			}
			// Add the line to the header list
			headers.addElement(line);
			line = nextLine;
		}

		// If we got a null above, it's the end
		if (line == null) {
			return null;
		}

		String name = null;
		String filename = null;
		String origname = null;
		String contentType = "text/plain";  // rfc1867 says this is the default

		Enumeration enu = headers.elements();
		while (enu.hasMoreElements()) {
			String headerline = (String) enu.nextElement();
			if (headerline.toLowerCase().startsWith("content-disposition:")) {
				// Parse the content-disposition line
				String[] dispInfo = extractDispositionInfo(headerline);
				// String disposition = dispInfo[0];  // not currently used
				name = dispInfo[1];
				filename = dispInfo[2];
				origname = dispInfo[3];
			}
			else if (headerline.toLowerCase().startsWith("content-type:")) {
				// Get the content type, or null if none specified
				String type = extractContentType(headerline);
				if (type != null) {
					contentType = type;
				}
			}
		}

		// Now, finally, we read the content (end after reading the boundary)
		if (filename == null) {
			// This is a parameter, add it to the vector of values
			// The encoding is needed to help parse the value
			return new ParamPart(name, in, boundary, encoding);
    	}
		else {
			// This is a file
			if (filename.equals("")) {
				filename = null; // empty filename, probably an "empty" file param
			}
			lastFilePart = new FilePart(name, in, boundary, contentType, filename, origname);
      		return lastFilePart;
		}
	}

	/**
	 * Extracts and returns the boundary token from a line.
	 *
	 * @return the boundary token.
	 */
	private String extractBoundary(String line) {
		// Use lastIndexOf() because IE 4.01 on Win98 has been known to send the
		// "boundary=" string multiple times.  Thanks to David Wall for this fix.
		int index = line.lastIndexOf("boundary=");
		if (index == -1) {
			return null;
		}
		String boundary = line.substring(index + 9);  // 9 for "boundary="
		if (boundary.charAt(0) == '"') {
			// The boundary is enclosed in quotes, strip them
			index = boundary.lastIndexOf('"');
			boundary = boundary.substring(1, index);
		}

		// The real boundary is always preceeded by an extra "--"
		boundary = "--" + boundary;

		return boundary;
	}

	/**
	 * Extracts and returns disposition info from a line, as a <code>String<code>
	 * array with elements: disposition, name, filename.
	 *
	 * @return String[] of elements: disposition, name, filename.
	 * @exception  IOException if the line is malformatted.
	 */
	private String[] extractDispositionInfo(String line) throws IOException {
		// Return the line's data as an array: disposition, name, filename
		String[] retval = new String[4];

		// Convert the line to a lowercase string without the ending \r\n
		// Keep the original line for error messages and for variable names.
		String origline = line;
		line = origline.toLowerCase();

		// Get the content disposition, should be "form-data"
		int start = line.indexOf("content-disposition: ");
		int end = line.indexOf(";");
		if (start == -1 || end == -1) {
			throw new IOException("Content disposition corrupt: " + origline);
		}
		String disposition = line.substring(start + 21, end).trim();
		if (!disposition.equals("form-data")) {
			throw new IOException("Invalid content disposition: " + disposition);
		}

		// Get the field name
		start = line.indexOf("name=\"", end);  // start at last semicolon
		end = line.indexOf("\"", start + 7);   // skip name=\"
		int startOffset = 6;
		if (start == -1 || end == -1) {
			// Some browsers like lynx don't surround with ""
			// Thanks to Deon van der Merwe, dvdm@truteq.co.za, for noticing
			start = line.indexOf("name=", end);
			end = line.indexOf(";", start + 6);
			if (start == -1) {
				throw new IOException("Content disposition corrupt: " + origline);
			}
			else if (end == -1) {
				end = line.length();
			}
			startOffset = 5;  // without quotes we have one fewer char to skip
		}
		String name = origline.substring(start + startOffset, end);

		// Get the filename, if given
		String filename = null;
		String origname = null;
		start = line.indexOf("filename=\"", end + 2);  // start after name
		end = line.indexOf("\"", start + 10);          // skip filename=\"
		if (start != -1 && end != -1) {                // note the !=
			filename = origline.substring(start + 10, end);
			origname = filename;
			// The filename may contain a full path.  Cut to just the filename.
			int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
			if (slash > -1) {
				filename = filename.substring(slash + 1);  // past last slash
			}
		}
		// Return a String array: disposition, name, filename, origname
		// empty filename denotes no file posted!
		retval[0] = disposition;
		retval[1] = name;
		retval[2] = filename;
		retval[3] = origname;
		return retval;
	}

	/**
	 * Extracts and returns the content type from a line, or null if the
	 * line was empty.
	 *
	 * @return content type, or null if line was empty.
	 * @exception  IOException if the line is malformatted.
	 */
	private static String extractContentType(String line) throws IOException {
		// Convert the line to a lowercase string
		line = line.toLowerCase();

		// Get the content type, if any
		// Note that Opera at least puts extra info after the type, so handle
		// that.  For example:  Content-Type: text/plain; name="foo"
		// Thanks to Leon Poyyayil, leon.poyyayil@trivadis.com, for noticing this.
		int end = line.indexOf(";");
		if (end == -1) {
			end = line.length();
		}
		return line.substring(13, end).trim();  // "content-type:" is 13
	}

	/**
	 * Read the next line of input.
	 *
	 * @return  a String containing the next line of input from the stream,
	 * or null to indicate the end of the stream.
	 * @exception IOException	if an input or output exception has occurred.
	 */
	private String readLine() throws IOException {
		StringBuffer sb = new StringBuffer();
		int n;
		String line;

		do {
			n = in.readLine(buf, 0, buf.length);  // does +=
			if (n != -1) {
				sb.append(new String(buf, 0, n, encoding));
			}
		} while (n == buf.length);  // loop only if the buffer was filled

		if (sb.length() == 0) {
			return null;  // nothing read, must be at the end of stream
		}

		// Cut off the trailing \n or \r\n
		// It should always be \r\n but IE5 sometimes does just \n
		// Thanks to Luke Blaikie for helping make this work with \n
		int len = sb.length();
		if (len >= 2 && sb.charAt(len - 2) == '\r') {
			sb.setLength(len - 2);  // cut \r\n
		}
		else if (len >= 1 && sb.charAt(len - 1) == '\n') {
			sb.setLength(len - 1);  // cut \n
		}
		return sb.toString();
	}
}
