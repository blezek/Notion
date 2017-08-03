/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import org.apache.log4j.Logger;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.pipeline.ImportService;
import org.rsna.util.FileUtil;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URL;

/**
 * An ImportService that polls a PolledHttpExportService to obtain files on request.
 */
public class PollingHttpImportService extends AbstractPipelineStage implements ImportService {

	static final Logger logger = Logger.getLogger(PollingHttpImportService.class);

	public File queue = null;
	String queuePath = "";
	boolean success = true;
	long lastPollTime = 0L;
	final long interval = 5000;
	URL url;

	/**
	 * Construct a PollingHttpImportService. This import service does
	 * not queue objects. It connects to the source when a request is
	 * received.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public PollingHttpImportService(Element element) throws Exception {
		super(element);
		if (root == null)
			logger.error(name+": No root directory was specified.");
		else {
			queue = new File(root, "queue");
			queue.mkdirs();
			queuePath = queue.getAbsolutePath();
		}

		//Get the destination url
		url = new URL(element.getAttribute("url").trim());
	}

	/**
	 * Get the number of objects in the import queue. Since this stage simply
	 * polls an external system there is not import queue, so to satisfy the
	 * contract of the ImportService interface, we just return zero.
	 * return zero.
	 */
	@Override
  public synchronized int getQueueSize() {
		return 0;
	}

	/**
	 * Get the next object available for processing.
	 * @return the next object available, or null if no object is available.
	 */
	@Override
  public synchronized FileObject getNextObject() {
		long time = System.currentTimeMillis();
		if (success || ((time - lastPollTime) > interval)) {
			lastPollTime = time;
			File file = getFile();
			if (file != null) {
				FileObject fileObject = FileObject.getInstance(file);
				fileObject.setStandardExtension();
				success = true;

				//Make sure we accept objects of this type.
				if (acceptable(fileObject)) return fileObject;

				//If we get here, this import service does not accept
				//objects of this type. Try to quarantine the
				//object, and if that fails, delete it.
				if (quarantine != null)  quarantine.insert(fileObject);
				else fileObject.getFile().delete();

				//Return null to ignore this object.
				//Note that success is still true. This will avoid
				//an interval delay before the getting the next object.
				return null;
			}
		}
		//No file was obtained. Set success to false to
		//avoid overpolling the source.
		success = false;
		return null;
	}

	/**
	 * Release a file from the queue. Note that other stages in the pipeline may
	 * have moved the file, so it is possible that the file will no longer exist.
	 * This method only deletes the file if it is still in the queue.
	 * @param file the file to be released, which must be the original file
	 * supplied by the ImportService.
	 */
	@Override
  public void release(File file) {
		if ((file != null)
				&& file.exists()
					&& file.getParentFile().getAbsolutePath().equals(queuePath)) {
			file.delete();
		}
	}

	/**
	 * Get HTML text displaying the current status of the stage.
	 * @return HTML text displaying the current status of the stage.
	 */
	@Override
  public String getStatusHTML() {
		String stageUniqueStatus =
			"<tr><td width=\"20%\">Queue size:</td>"
			+ "<td>"+FileUtil.getFileCount(queue)+"</td></tr>";
		if (lastPollTime != 0) {
			stageUniqueStatus +=
				"<tr><td width=\"20%\">Last poll time:</td>"
				+ "<td>"+StringUtil.getTime(lastPollTime,":")+"</td></tr>";
		}
		return super.getStatusHTML(stageUniqueStatus);
	}

	//Get a file from the external system.
	private File getFile() {
		File file = null;
		Socket socket = null;
		InputStream in = null;
		OutputStream out = null;
		try {
			//Establish the connection
			socket = new Socket(url.getHost(), url.getPort());
			socket.setTcpNoDelay(true);
			socket.setSoTimeout(0);
			in = socket.getInputStream();
			out = socket.getOutputStream();

			//Get the length of the input
			long length = getLong(in);

			if (length > 0) {
				BufferedInputStream is = new BufferedInputStream(in);
				file = File.createTempFile("IS-",".md",queue);
				FileOutputStream fos = null;
				try {
					fos = new FileOutputStream(file);
					int n;
					byte[] bbuf = new byte[1024];
					while ((length > 0) && ((n=is.read(bbuf,0,bbuf.length)) >= 0)) {
						fos.write(bbuf,0,n);
						length -= n;
					}
					fos.flush();
					fos.close();
					out.write(1); //send OK
					lastFileIn = file;
					lastFileOut = file;
					lastTimeIn = System.currentTimeMillis();
					lastTimeOut = lastTimeIn;
				}
				catch (Exception ex) {
					logger.warn("Exception while receiving a file", ex);
					try {
						out.write(0); //send not ok
						fos.close();
					}
					catch (Exception ignore) { logger.warn("Unable to send a negative response."); }
					file.delete();
				}
			}
		}
		catch (Exception ex) { logger.debug("Exception while polling", ex); }
		close(socket);
		return file;
	}

	//Close a socket and its streams if possible
	private void close(Socket socket) {
		if (socket != null) {
			try { socket.getInputStream().close(); }
			catch (Exception ignore) { noOp(); }
			try { socket.getOutputStream().close(); }
			catch (Exception ignore) { noOp(); }
			try { socket.close(); }
			catch (Exception ignore) { noOp(); }
		}
	}

	private void noOp() { }

	//Get a long value from an InputStream.
	//The long value is transmitted in little endian.
	private long getLong(InputStream in) {
		long el = 0;
		long x;
		try {
			for (int i=0; i<4; i++) {
				x = in.read();
				x = (x & 0x000000ff) << (8*i);
				el = x | el;
			}
			return el;
		}
		catch (Exception ex) { return 0; }
	}

}