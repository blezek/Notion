/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ui;

import java.io.File;
import java.io.FileFilter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An implementation of java.io.FileFilter that matches all directories
 * plus files ending with specific strings, including an asterisk wildcard,
 * which matches all files. It also provides methods for setting
 * and retrieving the matchable extensions.
 */
public class GeneralFileFilter implements FileFilter {

	String[] extensions;
	int maxCount;

	/**
	 * Class constructor creating a GeneralFileFilter with an empty array of extensions.
	 */
	public GeneralFileFilter() {
		this.extensions = new String[0];
		this.maxCount = -1;
	}

	/**
	 * Class constructor creating a GeneralFileFilter with an array of extensions.
	 * @param extensions the array of extensions.
	 */
	public GeneralFileFilter(String[] extensions) {
		this.extensions = extensions;
		this.maxCount = -1;
		filterExtensions();
	}

	/**
	 * Install a new array of extensions.
	 * @param extensions the array of extensions.
	 */
	public void setExtensions(String[] extensions) {
		this.extensions = extensions;
		filterExtensions();
	}

	/**
	 * Install a new array of extensions by splitting a comma-separated String.
	 * @param extensionString the String containing the list of extensions.
	 */
	public void setExtensions(String extensionString) {
		this.extensions = extensionString.split(",");
		filterExtensions();
	}

	/**
	 * Initialize the maximum file count, the maximum number of files that
	 * will be accepted by this filter.
	 * @param maxCount the maximum number of files that will be accepted
	 * by this filter.
	 */
	public void setMaxCount(int maxCount) {
		this.maxCount = maxCount;
	}

	/**
	 * Get the current array of extensions.
	 * @return the array of extensions.
	 */
	public String[] getExtensions() {
		return extensions;
	}

	/**
	 * Get the current array of extensions as a comma-separated String.
	 * @return the list of extensions as a comma-separated String.
	 */
	public String getExtensionString() {
		String string = extensions[0];
		for (int i=1; i<extensions.length; i++) string += "," + extensions[i];
		return string;
	}

	/**
	 * Add an extension to the current array of extensions.
	 * @param extension the extension to add to the current array.
	 */
	public void addExtension(String extension) {
		String[] temp = new String[extensions.length + 1];
		for (int i=0; i<extensions.length; i++) {
			temp[i] = extensions[i];
		}
		temp[extensions.length] = extension;
		extensions = temp;
		filterExtensions();
	}

	/**
	 * Determine whether a file matches the filter.
	 * <ul>
	 * <li>All directories except WINDOWS and WINNT match.
	 * <li>Any file ending with one of the extensions matches.
	 * <li>If one of the extensions is "[dcm]" and the
	 * filename consists of periods and numerals, it matches.
	 * <li>If any extension is an asterisk, the file matches.
	 * <li>If one of the extensions contains a string in
	 * curly brackets ( {...} ), it is treated as a regex
	 * and matched against the filename. Important:
	 * regexes are not allowed to contain commas.
	 * </ul>
	 * Note that all all matches except the regex match
	 * are not case-sensitive.
	 * @param file the file to test for a match
	 * @return whether the file matches the filter.
	 */
	public boolean accept(File file) {
		if (maxCount == 0) return false;
		String name = file.getName();
		if (file.isDirectory()) {
			if (name.equals("WINDOWS")) return false;
			if (name.equals("WINNT")) return false;
			if (maxCount == -1) return true;
			if (maxCount > 0) { maxCount--; return true; }
			return false;
		}
		String nameLC = name.toLowerCase();
		for (int i=0; i<extensions.length; i++) {
			if (extensions[i].equals("*")) {
				return (maxCount == -1) || (maxCount-- > 0);
			}
			if (nameLC.endsWith(extensions[i])) {
				return (maxCount == -1) || (maxCount-- > 0);
			}
			if (extensions[i].equals("[dcm]")) {
				String fname = nameLC.replaceAll("[\\d\\.]","");
				if (fname.length() == 0) {
					return (maxCount == -1) || (maxCount-- > 0);
				}
			}
			if (extensions[i].startsWith("{") && extensions[i].endsWith("}")) {
				String reg = extensions[i].substring(1, extensions[i].length()-2);
				Pattern pattern = Pattern.compile(reg);
				Matcher matcher = pattern.matcher(name);
				if (matcher.matches()) {
					return (maxCount == -1) || (maxCount-- > 0);
				}
			}
		}
		return false;
	}

	/**
	 * Return a String describing the filter for display by a chooser.
	 * @return the comma-separated list of extensions or, if the list is empty.
	 * the text "Directories only".
	 */
	public String getDescription() {
		String desc;
		if (extensions.length == 0) desc = "Directories only";
		else desc = getExtensionString();
		if (maxCount == -1) return desc;
		return desc + "  [maxCount = " + maxCount + "]";
	}

	//Filter the extensions array.
	private void filterExtensions() {
		for (int i=0; i<extensions.length; i++) {
			extensions[i] = extensions[i].trim();
			if (extensions[i].startsWith("{") && extensions[i].endsWith("}")) ; //do nothing - leave the extension alone.
			else if (extensions[i].indexOf("*") != -1) extensions[i] = "*";
			else extensions[i] = extensions[i].toLowerCase();
		}
	}

}
