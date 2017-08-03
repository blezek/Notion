/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.util;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Calendar;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Encapsulates static methods for working with strings. String methods
 * specifically for working with XML strings are found in XmlStringUtil.
 */
public class StringUtil {

	/**
	 * Make a string that defines a path from the root of the
	 * storage service's documents tree to a specific document
	 * directory. The path has the form: YYYY/MM/DDhhmmsssss
	 * where the values come from the current time and the slash
	 * character is actually either a slash or backslash, depending
	 * on the platform.
	 * @return the path string.
	 */
	public static String makePathFromDate() {
		Calendar now = Calendar.getInstance();
		return intToString(now.get(Calendar.YEAR), 4)
						 + File.separator
						 + intToString(now.get(Calendar.MONTH) + 1, 2)
						 + File.separator
						 + intToString(now.get(Calendar.DAY_OF_MONTH), 2)
						 + intToString(now.get(Calendar.HOUR_OF_DAY), 2)
						 + intToString(now.get(Calendar.MINUTE), 2)
						 + intToString(now.get(Calendar.SECOND), 2)
						 + intToString(now.get(Calendar.MILLISECOND), 3);
	}

	/**
	 * Make a string for the current time in the form: YYYYMMDDhhmmsssss.
	 * This method is suitable for defining a filename.
	 * @return the string.
	 */
	public static String makeNameFromDate() {
		Calendar now = Calendar.getInstance();
		return intToString(now.get(Calendar.YEAR), 4)
						 + intToString(now.get(Calendar.MONTH) + 1, 2)
						 + intToString(now.get(Calendar.DAY_OF_MONTH), 2)
						 + intToString(now.get(Calendar.HOUR_OF_DAY), 2)
						 + intToString(now.get(Calendar.MINUTE), 2)
						 + intToString(now.get(Calendar.SECOND), 2)
						 + intToString(now.get(Calendar.MILLISECOND), 3);
	}

	/**
	 * Make a datetime string for the current time in the form:
	 * YYYY.MM.DD[sep]hh:mm:ss, where [sep] is the supplied separator string.
	 * @param sep the separator string to insert between the date and the time.
	 * @return the string.
	 */
	public static String getDateTime(String sep) {
		return getDateTime(-1,sep);
	}

	/**
	 * Make a datetime string for the specified time in the form:
	 * YYYY.MM.DD[sep]hh:mm:ss, where [sep] is the supplied separator string.
	 * @param time the time in milliseconds, or -1 to use the current time.
	 * @param sep the separator string to insert between the date and the time.
	 * @return the string.
	 */
	public static String getDateTime(long time, String sep) {
		Calendar now = Calendar.getInstance();
		if (time != -1) now.setTimeInMillis(time);
		return intToString(now.get(Calendar.YEAR), 4)
				 + "."
				 + intToString(now.get(Calendar.MONTH) + 1, 2)
				 + "."
				 + intToString(now.get(Calendar.DAY_OF_MONTH), 2)
				 + sep
				 + intToString(now.get(Calendar.HOUR_OF_DAY), 2)
				 + ":"
				 + intToString(now.get(Calendar.MINUTE), 2)
				 + ":"
				 + intToString(now.get(Calendar.SECOND), 2);
	}

	/**
	 * Make a date string for the current date in the form:
	 * YYYY[sep]MM[sep]DD, where [sep] is the supplied separator string.
	 * @param sep the separator string to insert between the year, month, and day.
	 * @return the date string.
	 */
	public static String getDate(String sep) {
		return getDate(-1,sep);
	}

	/**
	 * Make a date string for the specified date in the form:
	 * YYYY[sep]MM[sep]DD, where [sep] is the supplied separator string.
	 * @param time the time in milliseconds, or -1 to use the current time.
	 * @param sep the separator string to insert between the year, month, and day.
	 * @return the date string.
	 */
	public static String getDate(long time, String sep) {
		Calendar now = Calendar.getInstance();
		if (time != -1) now.setTimeInMillis(time);
		return intToString(now.get(Calendar.YEAR), 4)
				 + sep
				 + intToString(now.get(Calendar.MONTH) + 1, 2)
				 + sep
				 + intToString(now.get(Calendar.DAY_OF_MONTH), 2);
	}

	/**
	 * Make a time string for the current time in the form:
	 * hh[sep]mm[sep]ss.sss, where [sep] is the supplied separator string.
	 * @param sep the separator string to insert between the hour, minute, and second.
	 * @return the time string.
	 */
	public static String getTime(String sep) {
		return getTime(-1,sep);
	}

	/**
	 * Make a time string for the specified time in the standard form:
	 * hh[sep]mm[sep]ss.sss, where [sep] is the supplied separator string.
	 * @param sep the separator string to insert between the hour, minute, and second.
	 * @return the time string.
	 */
	public static String getTime(long time, String sep) {
		Calendar now = Calendar.getInstance();
		if (time != -1) now.setTimeInMillis(time);
		return intToString(now.get(Calendar.HOUR_OF_DAY), 2)
				 + sep
				 + intToString(now.get(Calendar.MINUTE), 2)
				 + sep
				 + intToString(now.get(Calendar.SECOND), 2)
				 + "."
				 + intToString(now.get(Calendar.MILLISECOND), 3);
	}

	/**
	 * Convert a positive int to a String with at least n digits,
	 * padding with leading zeroes.
	 * @param theValue the int to be converted.
	 * @param nDigits the number of digits to return.
	 * @return the converted value.
	 */
	public static String intToString(int theValue, int nDigits) {
		String s = Integer.toString(theValue);
		int k = nDigits - s.length();
		for (int i=0; i<k; i++) s = "0" + s;
		return s;
	}

	/**
	 * Insert commas every 3 characters, starting at the low-order
	 * end of a long numeric string.
	 * @param s the string to be modified.
	 * @return the modified string.
	 */
	public static String insertCommas(String s) {
		int n = s.length();
		while ((n=n-3) > 0) s = s.substring(0,n) + "," + s.substring(n);
		return s;
	}

	/**
	 * Parse a string into a base-10 long, returning 0 if an error occurs.
	 * @param theString the string to be parsed.
	 * @return the parsed value, or zero if an error occurred in parsing.
	 */
	public static long getLong(String theString) {
		return getLong(theString, 0);
	}

	/**
	 * Parse a string into a base-10 long, returning a default value
	 * if an error occurs.
	 * @param theString the string to be parsed.
	 * @param defaultValue the value to be returned if the string does not parse.
	 * @return the parsed value, or the default value if an error occurred in parsing.
	 */
	public static long getLong(String theString, long defaultValue) {
		if (theString == null) return defaultValue;
		theString = theString.trim();
		if (theString.equals("")) return defaultValue;
		try { return Long.parseLong(theString); }
		catch (NumberFormatException e) { return defaultValue; }
	}

	/**
	 * Parse a string into a base-10 int, returning 0 if an error occurs.
	 * @param theString the string to be parsed.
	 * @return the parsed value, or zero if an error occurred in parsing.
	 */
	public static int getInt(String theString) {
		return getInt(theString, 0);
	}

	/**
	 * Parse a string into a base-10 int, returning a default value
	 * if an error occurs.
	 * @param theString the string to be parsed.
	 * @param defaultValue the value to be returned if the string does not parse.
	 * @return the parsed value, or the default value if an error occurred in parsing.
	 */
	public static int getInt(String theString, int defaultValue) {
		if (theString == null) return defaultValue;
		theString = theString.trim();
		if (theString.equals("")) return defaultValue;
		try { return Integer.parseInt(theString); }
		catch (NumberFormatException e) { return defaultValue; }
	}

	/**
	 * Parse a string into a base-16 int, returning 0 if an error occurs.
	 * @param theString the string to be parsed.
	 * @return the parsed value, or zero if an error occurred in parsing.
	 */
	public static int getHexInt(String theString) {
		return getHexInt(theString, 0);
	}

	/**
	 * Parse a string into a base-16 int, returning a default value
	 * if an error occurs.
	 * @param theString the string to be parsed.
	 * @param defaultValue the value to be returned if the string does not parse.
	 * @return the parsed value, or the default value if an error occurred in parsing.
	 */
	public static int getHexInt(String theString, int defaultValue) {
		if (theString == null) return defaultValue;
		if (theString.equals("")) return defaultValue;

		//Note: Integer.parseInt(s, 16) throws a NumberFormatException
		//if s denotes a 32-bit value with a leading 1, so we treat
		//the 8-character case separately.
		try {
			Integer i;
			if (theString.length() < 8) {
				return new Integer( Integer.parseInt(theString, 16) );
			}
			else {
				int high = Integer.parseInt(theString.substring(0,4), 16);
				int low = Integer.parseInt(theString.substring(4), 16);
				return new Integer( (high << 16) | low);
			}
		}
		catch (Exception e) { return defaultValue; }
	}

	/**
	 * Equivalent to <code>replace(string, null, true)</code>.
	 * @param string the String to be processed.
	 * @return a new string with the identifiers replace with values
	 * from the system environment.
	 */
	public static String replace(String string) {
		return replace(string, null, true);
	}

	/**
	 * Equivalent to <code>replace(string, table, false)</code>.
	 * @param string the String to be processed.
	 * @param table the replacement strings
	 * @return a new string with the identifiers replaced with values
	 * from the table.
	 */
	public static String replace(String string, Properties table) {
		return replace(string, table, false);
	}

	/**
	 * Replace coded identifiers with values from a table.
	 * Identifiers are coded as ${name}. The identifier is replaced
	 * by the string value in the table, using the name as the key.
	 * Identifiers which are not present in the table are left
	 * unmodified.
	 * @param string the String to be processed
	 * @param table the replacement strings (this argument can be null)
	 * @param includeEnvironmentVariables true to include the System
	 * environment variables in the replacement table
	 * @return a new string with the identifiers replaced with values
	 * from the table.
	 */
	public static String replace(String string, Properties table, boolean includeEnvironmentVariables) {
		try {
			Pattern pattern = Pattern.compile("\\$\\{\\w+\\}");
			Matcher matcher = pattern.matcher(string);
			StringBuffer sb = new StringBuffer();
			while (matcher.find()) {
				String group = matcher.group();
				String key = group.substring(2, group.length()-1).trim();
				String repl = null;
				if (table != null) repl = table.getProperty(key);
				if ((repl == null) && includeEnvironmentVariables) repl = System.getenv(key);
				if (repl == null) repl = Matcher.quoteReplacement(group);
				matcher.appendReplacement(sb, repl);
			}
			matcher.appendTail(sb);
			return sb.toString();
		}
		catch (Exception ex) { return string; }
	}

	/**
	 * Trim a string, returning the empty string if the supplied string is null.
	 * @param text the text to trim.
	 * @return the trimmed string.
	 */
	public static String trim(String text) {
		if (text == null) return "";
		return text.trim();
	}

	/**
	 * Escape the ampersands, angle brackets, single and double quotes
	 * in a string with their HTML entities.
	 * @param text the string to be escaped.
	 * @return the modified text.
	 */
	public static String displayable(String text) {
		if (text == null) return "null";
		return
			text.replaceAll("&","&amp;")
					.replaceAll("<","&lt;")
						.replaceAll(">","&gt;")
							.replace("\"","&quot;")
								.replace("'","&apos;");
	}

	/**
	 * Make an acceptable directory name from a String. This method
	 * converts carets to underscores and removes all unacceptable characters.
	 * @param name the string containing the proposed name of a directory.
	 * @return an acceptable version of the name, or the empty string if
	 * an acceptable version cannot be created.
	 */
	public static String filterName(String name) {
		if (name == null) return "";
		name = name.replaceAll("\\^", "_"); //for DICOM name elements
		return name.replaceAll("[~\\\\/\\s]", "");
	}

	/**
	 * Make a tag string readable when rendered in HTML. This method
	 * escapes all the angle brackets and inserts spaces before and
	 * after tags so a browser will wrap the text.
	 * @param tagString the string containing tags.
	 * @return the readable tag string, or "null" if tagString is null.
	 */
	public static String makeReadableTagString(String tagString) {
		if (tagString == null) return "null";
		StringWriter sw = new StringWriter();
		char c;
		for (int i=0; i<tagString.length(); i++) {
			c = tagString.charAt(i);
			if (c == '<') sw.write(" &#60;");		//note the leading space
			else if (c == '>') sw.write("&#62; ");	//note the trailing space
			else if (c == '&') sw.write("&#38;");
			else if (c == '\"') sw.write("&#34;");
			else sw.write(c);
		}
		return sw.toString();
	}

	/**
	 * Make a string containing the stack trace from an Exception.
	 * @param ex the Exception to trace.
	 * @return the stack trace.
	 */
	public static String getStackTrace(Exception ex) {
		StringWriter sw = new StringWriter();
		ex.printStackTrace(new PrintWriter(sw));
		return sw.toString();
	}

	/**
	 * Remove double quotes if they enclose a string
	 * @param s the string.
	 * @return the string, without the enclosing double quotes.
	 */
	public static String removeEnclosingQuotes(String s) {
		if (s == null) return "";
		if (s.length() == 1) return "";
		if (s.startsWith("\"") && s.endsWith("\"")) {
			return s.substring(1, s.length() - 1);
		}
		return s;
	}

	private static Pattern[] patterns = new Pattern[]{
		// Script fragments
		Pattern.compile("<script>(.*?)</script>", Pattern.CASE_INSENSITIVE),
		// src='...'
		Pattern.compile("src\\s*=\\s*\\\'(.*?)\\\'", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
		Pattern.compile("src\\s*=\\s*\\\"(.*?)\\\"", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
		// lonely script tags
		Pattern.compile("</script>", Pattern.CASE_INSENSITIVE),
		Pattern.compile("<script(.*?)>", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
		// eval(...)
		Pattern.compile("eval\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
		// expression(...)
		Pattern.compile("expression\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
		// javascript:...
		Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
		// vbscript:...
		Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE),
		// onload(...)=...
		Pattern.compile("onload(.*?)=", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL)
	};

	/**
	 * Filter a string to remove cross-site scripting attacks
	 * @param string the string to filter.
	 * @return the filtered string.
	 */
	public static String filterXSS(String string) {
		if (string != null) {
			// NOTE: It's highly recommended to use the ESAPI library and
			// uncomment the following line to avoid encoded attacks.
			// string = ESAPI.encoder().canonicalize(string);

			// Avoid null characters
			string = string.replaceAll("\0", "");

			// Remove all sections that match a pattern
			for (Pattern scriptPattern : patterns){
				string = scriptPattern.matcher(string).replaceAll("");
			}
		}
		return string;
	}

	/**
	 * Filter a string to remove blocks surrounded by non-word characters
	 * @param string the string to filter.
	 * @return the filtered string.
	 */
    public static String filterNonWordBlocks(String string) {
		Pattern nwb = Pattern.compile("\\W\\w*\\W?", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
		return nwb.matcher(string).replaceAll("");
	}

}

