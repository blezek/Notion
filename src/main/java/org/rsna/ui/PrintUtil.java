/*---------------------------------------------------------------
 *  Copyright 2013 by the Radiological Society of North America
 *
 *  This source software is released under the terms of the
 *  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
 *----------------------------------------------------------------*/

package org.rsna.ui;

import javax.swing.RepaintManager;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

/**
 * A simple utility class that lets you very simply print
 * an arbitrary component. Just pass the component to the
 * PrintUtil.printComponent. The component you want to
 * print doesn't need a print method and doesn't have to
 * implement any interface or do anything special at all.
 * <P>
 * If you are going to be printing many times, it is marginally more efficient
 * to first do the following:
 * 
 * <PRE>
 * PrintUtil printHelper = new PrintUtil(theComponent);
 * </PRE>
 * 
 * then later do printHelper.print(). But this is a very tiny difference, so in
 * most cases just do the simpler
 * PrintUtil.printComponent(componentToBePrinted).
 * 
 * 7/99 Marty Hall, http://www.apl.jhu.edu/~hall/java/ May be freely used or
 * adapted.
 */

public class PrintUtil implements Printable {
  private Component componentToBePrinted;

  public static void printComponent(Component c) {
    new PrintUtil(c).print();
  }

  public PrintUtil(Component componentToBePrinted) {
    this.componentToBePrinted = componentToBePrinted;
  }

  public void print() {
    PrinterJob printJob = PrinterJob.getPrinterJob();
    printJob.setPrintable(this);
    if (printJob.printDialog()) {
      try {
        printJob.print();
      } catch (PrinterException pe) {
        System.out.println("Error printing: " + pe);
      }
    }
  }

  public int print(Graphics g, PageFormat pageFormat, int pageIndex) {
    if (pageIndex > 0) {
      return (NO_SUCH_PAGE);
    } else {
      Graphics2D g2d = (Graphics2D) g;
      g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
      disableDoubleBuffering(componentToBePrinted);
      componentToBePrinted.paint(g2d);
      enableDoubleBuffering(componentToBePrinted);
      return (PAGE_EXISTS);
    }
  }

  /**
   * The speed and quality of printing suffers dramatically if
   * any of the containers have double buffering turned on.
   * So this turns if off globally.
   */
  public static void disableDoubleBuffering(Component c) {
    RepaintManager currentManager = RepaintManager.currentManager(c);
    currentManager.setDoubleBufferingEnabled(false);
  }

  /**
   * Re-enables double buffering globally.
   */
  public static void enableDoubleBuffering(Component c) {
    RepaintManager currentManager = RepaintManager.currentManager(c);
    currentManager.setDoubleBufferingEnabled(true);
  }
}
