/*---------------------------------------------------------------
*  Copyright 2012 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ui;

import javax.swing.JComponent;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager2;
import java.util.Hashtable;
import java.util.LinkedList;

/**
 * A LayoutManager2 that puts components in rows and columns.
 * Each row must be terminated by a CRLF object obtained from
 * RowLayout.crlf(). This manager supports the alignmentX and
 * alignmentY properties of the components.
 */
public class RowLayout implements LayoutManager2 {
	private int horizontalGap = 10;
	private int verticalGap = 5;
	private float layoutAlignmentX = 0.0f;
	private float layoutAlignmentY = 0.0f;
	private Hashtable<Component,Integer> spans;

	/**
	 * Construct a RowLayout with default gaps and alignments.
	 * The default horizontalGap is 10.
	 * The default verticalGap is 5.
	 * The default layoutAlignmentX is 0.0.
	 * The default layoutAlignmentY is 0.0.
	 */
	public RowLayout() {
		init();
	}

	/**
	 * Construct a RowLayout, specifying the horizontalGap and verticalGap.
	 * The default layoutAlignmentX is 0.0.
	 * The default layoutAlignmentY is 0.0.
	 * @param horizontalGap the horizontal gap in pixels between columns.
	 * @param verticalGap the vertical gap in pixels between rows.
	 */
	public RowLayout(int horizontalGap, int verticalGap) {
		init();
		this.horizontalGap = horizontalGap;
		this.verticalGap = verticalGap;
	}

	/**
	 * Construct a RowLayout.
	 * @param horizontalGap the horizontal gap in pixels between columns.
	 * @param verticalGap the vertical gap in pixels between rows.
	 * @param layoutAlignmentX the preferred horizontal alignment of this component in its container.
	 * @param layoutAlignmentY the preferred vertical alignment of this component in its container.
	 */
	public RowLayout(int horizontalGap, int verticalGap, float layoutAlignmentX, float layoutAlignmentY) {
		init();
		this.horizontalGap = horizontalGap;
		this.verticalGap = verticalGap;
		this.layoutAlignmentX = layoutAlignmentX;
		this.layoutAlignmentY = layoutAlignmentY;
	}

	private void init() {
		spans = new Hashtable<Component,Integer>();
	}

	/**
	 * Get an object to end a row.
	 */
	public static JComponent crlf() {
		return new CRLF();
	}

	/**
	 * Get a constraint object to span multiple columns.
	 */
	public static Integer span(int colspan) {
		return new Integer( (colspan>0) ? colspan : 1 );
	}

	static boolean isCRLF(Component c) {
		return (c instanceof CRLF);
	}

	public void invalidateLayout(Container target) { }
	public void removeLayoutComponent(Component component) { spans.remove(component); }
	public float getLayoutAlignmentX(Container target) { return layoutAlignmentX; }
	public float getLayoutAlignmentY(Container target) { return layoutAlignmentY; }

	public void addLayoutComponent(String name, Component component) { }

	public void addLayoutComponent(Component component, Object span) {
		if ((span != null) && (span instanceof Integer)) {
			spans.put(component, (Integer)span);
		}
	}

	public Dimension preferredLayoutSize(Container parent) {
		return getLayoutSize(parent, horizontalGap, verticalGap, false);
	}

	public Dimension maximumLayoutSize(Container parent) {
		return preferredLayoutSize(parent);
	}

	public Dimension minimumLayoutSize(Container parent) {
		return preferredLayoutSize(parent);
	}

	public void layoutContainer(Container parent) {
		getLayoutSize(parent, horizontalGap, verticalGap, true);
	}

	public LinkedList<LinkedList<Component>> getRows(Container parent) {
		Component[] components = parent.getComponents();
		LinkedList<LinkedList<Component>> rows = new LinkedList<LinkedList<Component>>();
		LinkedList<Component> row = null;
		for (int i=0; i<components.length; i++) {
			if (row == null) {
				row = new LinkedList<Component>();
				rows.add( row );
			}
			Component c = components[i];
			row.add( c );
			if (c instanceof CRLF) row = null;
		}
		return rows;
	}

	private Dimension getLayoutSize(Container parent, int hGap, int vGap, boolean layout) {
		Dimension d;
		Component[] components = parent.getComponents();
		Insets insets = parent.getInsets();

		//First find the number of rows and columns.
		int maxRowLength = 0;
		int x = 0;
		int y = 0;
		for (int i=0; i<components.length; i++) {
			if (components[i] instanceof CRLF) {
				maxRowLength = Math.max(maxRowLength, x);
				x = 0; y++;
			}
			else {
				Integer span = spans.get(components[i]);
				if (span != null) x += span.intValue();
				else x++;
			}
		}

		//Now find the maximum height required for each row
		//and the maximum column span that appears on each row.
		int[] rowHeight = new int[y+1];
		int[] maxSpan = new int[y+1];
		int largestSpan = 1;
		for (int i=0; i<rowHeight.length; i++) {
			rowHeight[i] = 0;
			maxSpan[i] = 1;
		}
		y = 0;
		for (int i=0; i<components.length; i++) {
			if (components[i] instanceof CRLF) {
				y++;
			}
			else {
				d = components[i].getPreferredSize();
				rowHeight[y] = Math.max(rowHeight[y], d.height);
				Integer span = spans.get(components[i]);
				if (span != null) {
					int w = span.intValue();
					largestSpan = Math.max( largestSpan, w);
					maxSpan[y] = Math.max( maxSpan[y], w);
				}
			}
		}

		//Now find the maximum width required for each column
		//First do all the single column entries.
		int[] columnWidth = new int[maxRowLength];
		for (int i=0; i<columnWidth.length; i++) columnWidth[i] = 0;
		x = 0;
		for (int i=0; i<components.length; i++) {
			if (components[i] instanceof CRLF) {
				x = 0;
			}
			else {
				Integer span = spans.get(components[i]);
				if ((span == null) || (span.intValue() == 1)) {
					d = components[i].getPreferredSize();
					columnWidth[x] = Math.max(columnWidth[x], d.width);
					x++;
				}
				else x += span.intValue();
			}
		}

		//Now adjust the column widths for the multi-column spans.
		//The algorithm requires that we work in columns, starting at
		//the left. Within a column, we adjust the column width for
		//spans starting with the smallest span and working upward.
		//We always assign all the required extra space to the last
		//column of the span.
		if (largestSpan > 1) {
			for (int column=0; column<maxRowLength; column++) {
				for (int colspan=2; colspan<=largestSpan; colspan++) {
					x = 0;
					for (int i=0; i<components.length; i++) {
						if (components[i] instanceof CRLF) {
							x = 0;
						}
						else {
							Integer span = spans.get(components[i]);
							int w = (span != null) ? span.intValue() : 1;
							if (x == column) {
								if (w == colspan) {
									d = components[i].getPreferredSize();
									int spanWidth = hGap * (w - 1);
									for (int k=x; k<Math.min(x+w, columnWidth.length); k++) {
										spanWidth += columnWidth[k];
									}
									if (spanWidth < d.width) {
										int colToFix = Math.min(x+w, columnWidth.length) -1;
										columnWidth[colToFix] += d.width - spanWidth;
									}
								}
							}
							x += w;
						}
					}
				}
			}
		}

		//Now lay out the container
		int currentX = insets.left;
		int currentY = insets.top;
		int maxX = 0;
		x = 0;
		y = 0;
		for (int i=0; i<components.length; i++) {
			if (components[i] instanceof CRLF) {
				maxX = Math.max(maxX, currentX + insets.right);
				currentX = insets.left;
				currentY += rowHeight[y] + vGap;
				x = 0;
				y++;
			}
			else {
				//It's not a CRLF, lay it out.
				d = components[i].getPreferredSize();
				float xAlign = components[i].getAlignmentX();
				float yAlign = components[i].getAlignmentY();

				Integer span = spans.get(components[i]);
				int w = (span != null) ? span.intValue() : 1;
				int spanWidth = hGap * (w - 1);
				for (int k=x; k<x+w; k++) spanWidth += columnWidth[k];

				int leftMargin = (int) ((spanWidth - d.width) * xAlign);
				int topMargin = (int) ((rowHeight[y] - d.height) * yAlign);
				if (layout) {
					components[i].setBounds(currentX + leftMargin, currentY + topMargin, d.width, d.height);
				}
				currentX += spanWidth + hGap;
				x += w;
			}
		}
		return new Dimension(maxX - hGap, currentY + insets.bottom - vGap);
	}

	static class CRLF extends JComponent {
		public CRLF() {
			super();
			setVisible(false);
		}
	}

}
