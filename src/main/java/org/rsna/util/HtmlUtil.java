/*---------------------------------------------------------------
*  Copyright 2013 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.util;

/**
 * Static methods for creating HTML elements.
 */
public class HtmlUtil {

	static final String defaultURL = "/";
	static final String defaultIcon = "/icons/home.png";
	static final String defaultTarget = "_self";
	static final String defaultTitle = "Return to the home page";

	/**
	 * Generate a close box linking to the home page (/),
	 * using the _self target. This method calls the
	 * more general getCloseBox method.
	 * @return the HTML code for the close box div.
	 */
	public static String getCloseBox() {
		return getCloseBox(defaultURL, defaultIcon, defaultTarget, defaultTitle);
	}

	/**
	 * Generate a close box linking to a specified URL,
	 * using the _self target. This method calls the
	 * more general getCloseBox method.
	 * @return the HTML code for the close box div.
	 */
	public static String getCloseBox(String url) {
		return getCloseBox(url, defaultIcon, defaultTarget, defaultTitle);
	}

	/**
	 * Generate an HTML div with a close box.
	 * The div floats right and links to a new page.
	 * @param url the URL to load.
	 * @param target the target window.
	 * @param title the title for the close box.
	 * @return the HTML code for the close box div.
	 */
	public static String getCloseBox(String url, String target, String title) {
		return getCloseBox(url, defaultIcon, target, title);
	}

	/**
	 * Generate an HTML div with a close box.
	 * The div floats right and links to a new page.
	 * @param url the URL to load.
	 * @param icon the URL of the icon to display.
	 * @param target the target window.
	 * @param title the title for the close box.
	 * @return the HTML code for the close box div.
	 */
	public static String getCloseBox(String url, String icon, String target, String title) {
		if (url == null) url = defaultURL;
		if (icon == null) icon = defaultIcon;
		if (target == null) target = defaultTarget;
		if (title == null) title = defaultTitle;
		return
			"<div style=\"float:right;\">\n"
		  + " <img src=\""+icon+"\"\n"
		  + "  onclick=\"window.open('"+url+"','"+target+"');\"\n"
		  + "  title=\""+title+"\"\n"
		  + "  style=\"margin:2\"/>\n"
		  + "</div>\n";
	}

}