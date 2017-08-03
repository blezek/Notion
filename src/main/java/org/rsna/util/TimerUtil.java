/*---------------------------------------------------------------
*  Copyright 2013 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.util;

/**
 * A class for measuring time intervals using the System nanosecond timer.
 */
public class TimerUtil {

	static long startTime = 0;
	static long lapStartTime = 0;

	/**
	 * Reset the timer and return the current time.
	 * @return the start time. Note that this time
	 * is arbitrary. It has no relationship to
	 * actual wall-clock time. It can only be used
	 * for relative time measurements.
	 */
	public static long reset() {
		startTime = System.nanoTime();
		lapStartTime = startTime;
		return startTime;
	}

	/**
	 * Get the interval from the last reset to the current time
	 * using the System nanosecond timer.
	 * @return the interval in nanoseconds from the last reset
	 * to the current time.
	 */
	public static long getTotalTime() {
		long currentTime = System.nanoTime();
		return currentTime - startTime;
	}

	/**
	 * Get the interval from the last reset to the current time
	 * using the System nanosecond timer.
	 * @return the interval in nanoseconds from the last reset
	 * to the current time as a string with the supplied text
	 * appended to the string.
	 */
	public static String getTotalTimeText(String text) {
		long time = getTotalTime();
		return getText(time, text);
	}

	/**
	 * Get the interval from the last lap start time to the current time,
	 * and reset the lap start time.
	 * @return the interval from the last reset to the current time.
	 */
	public static long getLapTime() {
		long currentTime = System.nanoTime();
		long lapTime = currentTime - lapStartTime;
		lapStartTime = currentTime;
		return lapTime;
	}

	/**
	 * Get the interval from the last lap start time to the current time
	 * as a text string and reset the lap start time.
	 * @return the interval from the last reset to the current time
	 * as a string with the supplied text appended to the string..
	 */
	public static String getLapTimeText(String text) {
		long time = getLapTime();
		return getText(time, text);
	}

	/**
	 * Get a string formatting a time.
	 * @param time the time to be displayed.
	 * @param text any text describing the time.
	 */
	public static String getText(long time, String text) {
		text = text.trim();
		if (!text.equals("")) text = ": "+text;
		double dtime = (time)/1000000.0;
		return String.format("%10.6f ms%s", dtime, text);
	}

}

