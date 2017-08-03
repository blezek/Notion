/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ui;

import javax.swing.JTree;
import javax.swing.tree.TreeSelectionModel;

import java.io.File;

/**
 * A JTree that displays a directory tree with filenames.
 */
public class FileTree extends JTree {

	/**
	 * Class constructor; creates a JTree with a FileTreeModel.
	 * @param model the directory tree model
	 */
	public FileTree(FileTreeModel model) {
		super(model);
		TreeSelectionModel tsm = getSelectionModel();
		tsm.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		setSelectionModel(tsm);
	}

	/**
	 * Override the JTree method call to display the text name of a node.
	 * @param value - the Object to convert to text
	 * @param selected - true if the node is selected
	 * @param expanded - true if the node is expanded
	 * @param leaf - true if the node is a leaf node
	 * @param row - an integer specifying the node's display row, where 0 is the first row in the display
	 * @param hasFocus - true if the node has the focus
	 * @return the String representation of the node's value
	 */
	public String convertValueToText(
					Object value,
					boolean selected,
					boolean expanded,
					boolean leaf,
					int row,
					boolean hasFocus) {
		if((value != null) && (value instanceof File)) {
			String text = ((File)value).getName();
			if (text.equals("")) text = ((File)value).toString();
			return text;
		}
		return "";
	}
}


