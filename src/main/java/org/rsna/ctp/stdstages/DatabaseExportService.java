/*---------------------------------------------------------------
*  Copyright 2009 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import org.apache.log4j.Logger;
import org.rsna.ctp.Configuration;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.objects.XmlObject;
import org.rsna.ctp.objects.ZipObject;
import org.rsna.ctp.pipeline.AbstractQueuedExportService;
import org.rsna.ctp.pipeline.PipelineStage;
import org.rsna.ctp.pipeline.QueueManager;
import org.rsna.ctp.pipeline.Status;
import org.rsna.ctp.pipeline.StorageService;
import org.rsna.ctp.stdstages.database.DatabaseAdapter;
import org.rsna.ctp.stdstages.database.UIDResult;
import org.rsna.ctp.stdstages.storage.StoredObject;
import org.rsna.server.HttpRequest;
import org.rsna.server.HttpResponse;
import org.rsna.service.HttpService;
import org.rsna.service.Service;
import org.rsna.util.StringUtil;
import org.w3c.dom.Element;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * The Thread that exports DicomObjects to a database.
 */
public class DatabaseExportService extends AbstractQueuedExportService {

	static final Logger logger = Logger.getLogger(DatabaseExportService.class);

	static final int defaultInterval = 5000;
	static final int minInterval = 1000;
	static final int maxInterval = 2 * defaultInterval;

	static final int minPoolSize = 1;
	static final int maxPoolSize = 10;

	int interval = defaultInterval;
	Exporter[] exporters = null;
	int poolSize = minPoolSize;
	String fileStorageServiceID;
	String adapterClassName = "";
	HttpService verifierService = null;

	volatile long lastElapsedTime = -1;

	/**
	 * Class constructor; creates a new instance of the DatabaseExportService.
	 */
	public DatabaseExportService(Element element) throws Exception {
		super(element);

		if (root != null) {
			interval = StringUtil.getInt(element.getAttribute("interval").trim());
			if ((interval < minInterval) || (interval > maxInterval)) interval = defaultInterval;
			poolSize = StringUtil.getInt(element.getAttribute("poolSize").trim());
			if (poolSize < minPoolSize) poolSize = minPoolSize;
			if (poolSize > maxPoolSize) poolSize = maxPoolSize;
			adapterClassName = element.getAttribute("adapterClass").trim();
			fileStorageServiceID = element.getAttribute("fileStorageServiceID").trim();
			int port = StringUtil.getInt(element.getAttribute("port").trim());
			boolean ssl = element.getAttribute("ssl").trim().equals("yes");
			boolean requireAuthentication = element.getAttribute("requireAuthentication").trim().equals("yes");
			if (port != 0) startVerifierService(ssl, port, requireAuthentication);

		}
		else {
			logger.error(name+": Missing root directory attribute.");
			throw new Exception(name+": Missing root directory attribute.");
		}
	}

	/**
	 * Stop the pipeline stage.
	 */
	@Override
  public void shutdown() {
		if (verifierService != null) verifierService.stopServer();
		stop = true;
	}

	/**
	 * Start the Exporter threads.
	 */
	@Override
  public void start() {
		exporters = new Exporter[poolSize];
		for (int i=0; i<poolSize; i++) {
			DatabaseAdapter dba = null;
			try {
				Class adapterClass = Class.forName(adapterClassName);
				try {
					Class[] signature = { Element.class };
					Object[] args = { element };
					dba = (DatabaseAdapter)adapterClass.getConstructor(signature).newInstance(args);
				}
				catch (Exception unableWithElement) {
					try { dba = (DatabaseAdapter)adapterClass.newInstance(); }
					catch (Exception unableWithEmptyContstructor) {
						logger.error(name+": Unable to load the Database class: " + adapterClassName);
					}
				}
				if (dba != null) {
					exporters[i] = new Exporter(dba, i);
					exporters[i].start();
				}
			}
			catch (Exception ex) {
				logger.warn("DatabaseAdapter class not found: "+adapterClassName);
			}
		}
	}

	/**
	 * Check whether the pipeline has shut down. Note: the instruction to
	 * stop is set in the AbstractPipelineStage ancestor class.
	 * @return true if the pipeline has cleanly shut down; false otherwise.
	 */
	@Override
  public boolean isDown() {
		for (int i=0; i<exporters.length; i++) {
			if (!exporters[i].getState().equals(Thread.State.TERMINATED)) return false;
		}
		return true;
	}

	//A class to export files to the database.
	//One instance of this class is instantiated for each member of the
	//pool. Each Exporter has its own DatabaseAdapter instance.
	class Exporter extends Thread {
		DatabaseAdapter dba;
		int id;

		public Exporter(DatabaseAdapter dba, int id) {
			super("DatabaseExportService Exporter "+id);
			this.dba = dba;
			this.id = id;
			dba.setID(id);
		}

		@Override
    public void run() {
			logger.info(name+": Exporter["+id+"]: Started");
			File file = null;

			while (!stop && !interrupted()) {
				try {
					if ((getQueueSize()>0) && dba.connect().equals(Status.OK)) {
						while ((file = getNextFile()) != null) {
							long startTime = System.nanoTime();
							Status result = export(file);
							lastElapsedTime = System.nanoTime() - startTime;
							if (result.equals(Status.FAIL)) {
								//Something is wrong with the file.
								//Log a warning and quarantine the file.
								logger.warn(name+" Exporter["+id+"]: Unable to export "+file);
								if (quarantine != null) quarantine.insert(file);
								else file.delete();
							}
							else if (result.equals(Status.RETRY)) {
								//Something is wrong, but probably not with the file.
								//Let the thread sleep and then start up again with
								//the next file.
								//Note that the file has been removed from the queue,
								//so it is necessary to requeue it. This has the
								//effect of moving it to the end of the queue.
								getQueueManager().enqueue(file);
								//Note that enqueuing a file does not delete it
								//from the source location, so we must delete it.
								file.delete();
								break;
							}
							else release(file);
						}
						dba.disconnect();
					}
					if (!stop) sleep(interval);
					//Recount the queue in case it has been corrupted by
					//someone copying files into the queue directories by hand.
					//To keep from doing this when it doesn't really matter and
					//it might take a long time, only do it when the remaining
					//queue is small.
					if (!stop && (id == 0) && (getQueueSize() < 10)) recount();
				}
				catch (Exception e) { break; }
			}
			dba.shutdown();
		}

		private Status export(File fileToExport) {

			//Get the object as whatever FileObject subclass it is.
			FileObject fileObject = FileObject.getInstance(fileToExport);

			//Get the information about the stored object if possible
			StorageService referencedStorageService = null;
			PipelineStage ps = Configuration.getInstance().getRegisteredStage(fileStorageServiceID);
			if ((ps != null) && (ps instanceof StorageService)) referencedStorageService = (StorageService)ps;
			File file = null;
			String url = null;
			if (referencedStorageService != null) {
				if (referencedStorageService instanceof FileStorageService) {
					String embeddedFilename = QueueManager.getEmbeddedFilename(fileObject.getFile().getName(), false);
					if (!embeddedFilename.equals("")) {
						FileStorageService fss = (FileStorageService)referencedStorageService;
						StoredObject so = fss.getStoredObject(fileObject, embeddedFilename);
						if (so != null) {
							file = so.file;
							url = so.url;
						}
					}
				}
				else if (referencedStorageService instanceof BasicFileStorageService) {
					String sopInstanceUID = fileObject.getSOPInstanceUID();
					if (sopInstanceUID != null) {
						BasicFileStorageService bfss = (BasicFileStorageService)referencedStorageService;
						file = bfss.getFileForUID(sopInstanceUID);
					}
				}
			}
			try {
				//See if it's a DicomObject.
				if (fileObject instanceof DicomObject) {
					return dba.process((DicomObject)fileObject, file, url);
				}

				//If we get here, see if it's a ZipObject.
				if (fileObject instanceof ZipObject) {
					return dba.process((ZipObject)fileObject, file, url);
				}

				//If we get here, see if it's an XmlObject.
				if (fileObject instanceof XmlObject) {
					return dba.process((XmlObject)fileObject, file, url);
				}

				//If we get here, it's just a plain FileObject.
				return dba.process(fileObject, file, url);
			}
			catch (Exception failure) {
				logger.warn("Exception received from "+adapterClassName+".process call.");
				return Status.RETRY;
			}
		}
	}

	/**
	 * Get HTML text displaying the active status of the stage.
	 * @param childUniqueStatus the status of the stage of which
	 * this class is the parent.
	 * @return HTML text displaying the active status of the stage.
	 */
	@Override
  public synchronized String getStatusHTML(String childUniqueStatus) {
		String stageUniqueStatus = "";
		if (lastElapsedTime >= 0) {
			long et = lastElapsedTime / 1000000;
			stageUniqueStatus =
				  "<tr><td width=\"20%\">Last export elapsed time:</td>"
				+ "<td>"
				+ String.format("%d msec", et)
				+ "</td></tr>";
		}
		return super.getStatusHTML(childUniqueStatus + stageUniqueStatus);
	}

	//Start the Verifier service to service GET requests to verify that a
	//set of SOPInstanceUIDs have been received and processed by the
	//DatabaseAdapter (and therefore, the database).
	private void startVerifierService(boolean ssl, int port, boolean requireAuthentication) {
		try {
			Verifier verifier = new Verifier(requireAuthentication);
			verifierService = new HttpService(ssl, port, verifier, name);
			verifierService.start();
		}
		catch (Exception ex) {
			logger.error(name + ": Unable to instantiate the VerifierService on port "+port);
		}
	}

	class Verifier implements Service {

		boolean requireAuthentication;

		public Verifier(boolean requireAuthentication) {
			this.requireAuthentication = requireAuthentication;
		}

		@Override
    public void process(HttpRequest req, HttpResponse res) {
			if (!requireAuthentication || req.userHasRole("import")) {
				String result = verify(req);
				if (result != null) {
					res.write(result);
					res.setContentType("xml");
				}
				else res.setResponseCode(HttpResponse.notfound); //error
			}
			else { res.setResponseCode(HttpResponse.unauthorized); }
			res.send();
		}

		private String verify(HttpRequest req) {
			String uidsParam = req.getParameter("uids");
			if (uidsParam != null) {

				String[] uids = uidsParam.replaceAll("\\s","").split(";");
				HashSet<String> set = new HashSet<String>();
				for (int i=0; i<uids.length; i++) set.add(uids[i]);

				DatabaseAdapter dba;
				try {
					Class adapterClass = Class.forName(adapterClassName);
					dba = (DatabaseAdapter)adapterClass.newInstance();
					if (dba.connect().equals(Status.OK)) {
						StringBuffer sb = new StringBuffer();
						sb.append("<result>\n");
						Map<String, UIDResult> resultMap = dba.uidQuery(set);
						if (resultMap != null) {
							Set<String> uidKeySet = resultMap.keySet();
							Iterator<String> it = uidKeySet.iterator();
							while (it.hasNext()) {
								String uid = it.next();
								UIDResult uidResult = resultMap.get(uid);
								if ((uidResult != null) && uidResult.isPRESENT()) {
									sb.append("  <file uid=\""+uid+"\"\n"
											+ "        date=\""+uidResult.getDateTime()+"\"\n"
											+ "        digest=\""+uidResult.getDigest()+"\"/>\n");
								}
							}
						}
						dba.disconnect();
						sb.append("</result>");
						return sb.toString();
					}
					else logger.warn(name+": Unable to connect to the database");
				}
				catch (Exception ex) {
					logger.error(name+": Unable to load the Database class: " + adapterClassName);
					return null;
				}
			}
			return null;
		}
	}

}
