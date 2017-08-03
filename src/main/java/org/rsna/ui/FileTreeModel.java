/*---------------------------------------------------------------
*  Copyright 20054 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*
*  Adapted from code by Christian Kaufhold (ch-kaufhold@gmx.de)
*----------------------------------------------------------------*/

package org.rsna.ui;

import javax.swing.event.EventListenerList;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import java.io.File;
import java.io.FileFilter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A simple static TreeModel containing a java.io.File directory structure.
 */
public class FileTreeModel
	implements TreeModel, Serializable, Cloneable {

	protected EventListenerList listeners;
	private static final Object LEAF = new Serializable() { };
	private Map map;
	private File root;
	private FileFilter filter;

	public FileTreeModel(File root, GeneralFileFilter filter) {
		this.root = root;
		this.filter = filter;
		this.map = new HashMap();
		if (!root.isDirectory()) map.put(root, LEAF);
		this.listeners = new EventListenerList();
	}

	@Override
  public Object getRoot() {
		return root;
	}

	@Override
  public boolean isLeaf(Object node) {
		return map.get(node) == LEAF;
	}

	@Override
  public int getChildCount(Object node) {
		List children = children(node);
		if (children == null) return 0;
		return children.size();
	}

	@Override
  public Object getChild(Object parent, int index) {
		return children(parent).get(index);
	}

	@Override
  public int getIndexOfChild(Object parent, Object child) {
		return children(parent).indexOf(child);
	}

	protected List children(Object node) {
		File f = (File)node;
		Object value = map.get(f);
		if (value == LEAF) return null;
		List children = (List)value;
		if (children == null) {
			File[] c = f.listFiles(filter);
			if (c != null) {
				children = new ArrayList(c.length);
				for (int len = c.length, i = 0; i < len; i++) {
					children.add(c[i]);
					if (!c[i].isDirectory()) map.put(c[i], LEAF);
				}
			}
			else children = new ArrayList(0);
			map.put(f, children);
		}
		return children;
	}

	@Override
  public void valueForPathChanged(TreePath path, Object value) { }

	@Override
  public void addTreeModelListener(TreeModelListener l){
		listeners.add(TreeModelListener.class, l);
	}

	@Override
  public void removeTreeModelListener(TreeModelListener l) {
		listeners.remove(TreeModelListener.class, l);
	}

	@Override
  public Object clone() {
		try {
			FileTreeModel clone = (FileTreeModel)super.clone();
			clone.listeners = new EventListenerList();
			clone.map = new HashMap(map);
			return clone;
		}
		catch (CloneNotSupportedException e) {
			throw new InternalError();
		}
	}

}