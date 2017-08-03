/*---------------------------------------------------------------
*  Copyright 2013 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ui;

import javax.swing.SwingUtilities;
import javax.swing.event.EventListenerList;

import java.io.File;
import java.util.EventListener;

/**
 * An extension of PropertiesFile that provides PropertyEvents
 * to listeners.
 */
public class ApplicationProperties extends PropertiesFile {

	EventListenerList listenerList;
	boolean notifyOnChange = false;

	/**
	 * Class constructor; creates a new Properties object with
	 * automatic notification on changes turned off and
	 * loads the properties file, ignoring exceptions.
	 *
	 * @param file the properties file.
	 */
	public ApplicationProperties(File file) {
		this(file, false);
	}

	/**
	 * Class constructor; creates a new Properties object with
	 * the specified automatic notification on changes and
	 * loads the properties file, ignoring exceptions.
	 *
	 * @param file the properties file.
	 * @param notifyOnChange true if every property change is
	 * to generate PropertyEvents; false otherwise.
	 */
	public ApplicationProperties(File file, boolean notifyOnChange) {
		super(file);
		this.notifyOnChange = notifyOnChange;
		listenerList = new EventListenerList();
		load();
	}

	/**
	 * Set the notifyOnChange flag.
	 * @param notifyOnChange true if every property change is
	 * to generate PropertyEvents; false otherwise.
	 */
	public void setNotifyOnChange(boolean notifyOnChange) {
		this.notifyOnChange = notifyOnChange;
	}

	/**
	 * Set a property, and if notifyOnChange is set, send
	 * PropertyEvents to the listeners.
	 * @return the previous value of the property, or null if the
	 * property had no previous value.
	 */
	@Override
  public Object setProperty(String key, String value) {
		Object object = super.setProperty(key, value);
		if (notifyOnChange) sendPropertyEvent(this,key, value, (String)object);
		return object;
	}

	/**
	 * Add a PropertyListener to the listener list.
	 * @param listener the PropertyListener.
	 */
	public void addPropertyListener(PropertyListener listener) {
		listenerList.add(PropertyListener.class, listener);
	}

	/**
	 * Send a PropertyEvent to the PropertyListeners, encouraging
	 * them to check the properties object.
	 */
	public void notifyListeners() {
		sendPropertyEvent(this, null, null, null);
	}

	/**
	 * Remove a PropertyListener from the listener list.
	 * @param listener the PropertyListener.
	 */
	public void removePropertyListener(PropertyListener listener) {
		listenerList.remove(PropertyListener.class, listener);
	}

	/**
	 * Send a PropertyEvent to all PropertyListeners. This method sends the PropertyEvent
	 * in the Event thread to make it thread-safe for GUI components.
	 * @param object the Object sending the PropertyEvent.
	 * @param key the property that changed.
	 * @param newValue the new value of the property.
	 * @param oldValue the old value of the property.
	 */
	public void sendPropertyEvent(Object object, String key, String newValue, String oldValue) {
		final PropertyEvent event = new PropertyEvent(object,key,newValue,oldValue);
		final EventListener[] listeners = listenerList.getListeners(PropertyListener.class);
		Runnable fireEvents = new Runnable() {
			@Override
      public void run() {
				for (int i=0; i<listeners.length; i++) {
					((PropertyListener)listeners[i]).propertyChanged(event);
				}
			}
		};
		SwingUtilities.invokeLater(fireEvents);
	}

}
