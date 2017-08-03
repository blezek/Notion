/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ui;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.event.EventListenerList;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.text.Position;
import javax.swing.tree.TreePath;

import java.io.File;
import java.util.EventListener;
import java.util.Properties;

/**
 * A JScrollPane containing a directory tree with file selection and event notification.
 */
public class DirectoryPane extends JScrollPane implements TreeSelectionListener {

	GeneralFileFilter filter;
	FileTree tree;
	FileTreeModel fileTreeModel;
	File[] roots;
	int currentRootIndex;
	String currentPath;
	File currentSelection = null;
	Properties properties;
	EventListenerList listenerList;

	/**
	 * Class constructor; creates a new JScrollPane and loads it with a tree showing
	 * to the current directory.
	 * @param filter the list of filename extensions to include in the tree.
	 * @param currentPath the path to expand in the tree.
	 */
	public DirectoryPane(GeneralFileFilter filter, String currentPath) {
		super();
		listenerList = new EventListenerList();
		roots = File.listRoots();
		this.filter = filter;
		this.currentPath = currentPath;
		currentRootIndex = getRootIndexFromPath(currentPath);
		showTree();
		showCurrentPath(currentPath);
	}

	/**
	 * Get an array of file system roots.
	 * @return the array of file system roots.
	 */
	public File[] getRoots() {
		return roots;
	}

	/**
	 * Determine whether a file is a root.
	 * @param file the file to check.
	 * @return true if the file is a file system root; false otherwise.
	 */
	//
	public boolean isRoot(File file) {
		for (int i=0; i<roots.length; i++) {
			if (file.equals(roots[i])) return true;
		}
		return false;
	}

	/**
	 * Get the index of the current file system root that is shown in the tree.
	 * @return the index of the current file system root in the list returned by getRoots().
	 */
	public int getCurrentRootIndex() {
		return currentRootIndex;
	}

	/**
	 * Get the current file system root that is shown in the tree.
	 * @return the current file system root.
	 */
	public File getCurrentRoot() {
		return roots[currentRootIndex];
	}

	/**
	 * Get a File object pointing to the currently selected file in the tree.
	 * @return the currently selected file, or null if no file is selected.
	 */
	public File getCurrentSelection() {
		return currentSelection;
	}

	/**
	 * Get the current filter used to select files for display in the tree.
	 * @return the current file filter.
	 */
	public GeneralFileFilter getFileFilter() {
		return filter;
	}

	/**
	 * Redisplay the directory tree.
	 */
	public void reloadTree() {
		showTree();
		showCurrentPath(currentPath);
	}

	/**
	 * Change the file system root displayed in the tree.
	 * @param index the index of the file system root in the array returned by getRoots().
	 * @return the index of the current file system root after the tree is redisplayed.
	 */
	public int changeRoot(int index) {
		if (index != currentRootIndex) {
			if (roots[index].exists()) {
				currentRootIndex = index;
				showTree();
				if (index == getRootIndexFromPath(currentPath)) {
					showCurrentPath(currentPath);
				}
			}
			else JOptionPane.showMessageDialog(
				this,roots[index].getAbsolutePath() + " is not available.");
		}
		return currentRootIndex;
	}

	// Determine the root from a path string and
	// to return the root index for that root.
	private int getRootIndexFromPath(String path) {
		String rootPath = path.substring(0,path.indexOf(File.separator)+1);
		for (int i=0; i<roots.length; i++) {
			if (rootPath.equals(roots[i].getAbsolutePath())) return i;
		}
		return 0;
	}

	// Display the tree for the current root index
	private void showTree() {
		fileTreeModel = new FileTreeModel(roots[currentRootIndex],filter);
		tree = new FileTree(fileTreeModel);
		tree.addTreeSelectionListener(this);
		this.setViewportView(tree);
	}

	// Walk the tree for a path and show the end point,
	// scrolling the tree to make the end point visible.
	private void showCurrentPath(String path) {
		String separator = "\\\\";
		if (File.separator.equals("/")) separator = "/";
		String[] nodes = path.split(separator);
		TreePath treePath = null;
		TreePath temp = null;
		int row = 0;
		try {
			for (int i=0; i<nodes.length; i++) {
				if ((temp=tree.getNextMatch(nodes[i],row,Position.Bias.Forward)) == null) break;
				treePath = temp;
				tree.expandPath(treePath);
				row = tree.getRowForPath(treePath) + 1;
			}
		}
		catch (Exception ex) { treePath = null; }
		if (treePath != null) tree.scrollPathToVisible(treePath);
	}

	/**
	 * The TreeSelectionEvent listener.
	 * @param event the event.
	 */
	public void valueChanged(TreeSelectionEvent event) {
		Object object = tree.getLastSelectedPathComponent();
		if ((object != null) && (object instanceof File)) {
			File file = (File)object;
			if (isRoot(file)) reloadTree();
			currentPath = file.getAbsolutePath();
			currentSelection = file;
			sendFileEvent(new FileEvent(this,FileEvent.SELECT,file));
		}
	}

	/**
	 * Add a FileListener to the listener list.
	 * @param listener the FileListener.
	 */
	public void addFileListener(FileListener listener) {
		listenerList.add(FileListener.class, listener);
	}

	/**
	 * Remove a FileListener from the listener list.
	 * @param listener the FileListener.
	 */
	public void removeFileListener(FileListener listener) {
		listenerList.remove(FileListener.class, listener);
	}

	// Send a FileEvent to all FileListeners.
	// This event is sent in the calling thread because events in
	// this class are generated in the event thread already,
	// making them safe for GUI updates.
	public void sendFileEvent(FileEvent event) {
		EventListener[] listeners = listenerList.getListeners(FileListener.class);
		for (int i=0; i<listeners.length; i++) {
			((FileListener)listeners[i]).fileEventOccurred((FileEvent)event);
		}
	}
}
