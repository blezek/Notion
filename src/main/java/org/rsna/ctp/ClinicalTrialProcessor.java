/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.rsna.ctp.servlets.ConfigurationServlet;
import org.rsna.ctp.servlets.DBVerifierServlet;
import org.rsna.ctp.servlets.DecipherServlet;
import org.rsna.ctp.servlets.DicomAnonymizerServlet;
import org.rsna.ctp.servlets.IDMapServlet;
import org.rsna.ctp.servlets.LookupServlet;
import org.rsna.ctp.servlets.ObjectTrackerServlet;
import org.rsna.ctp.servlets.QuarantineServlet;
import org.rsna.ctp.servlets.ScriptServlet;
import org.rsna.ctp.servlets.ServerServlet;
import org.rsna.ctp.servlets.ShutdownServlet;
import org.rsna.ctp.servlets.StatusServlet;
import org.rsna.ctp.servlets.SummaryServlet;
import org.rsna.server.Authenticator;
import org.rsna.server.HttpServer;
import org.rsna.server.ServletSelector;
import org.rsna.server.Users;
import org.rsna.servlets.ApplicationServer;
import org.rsna.servlets.EnvironmentServlet;
import org.rsna.servlets.LogServlet;
import org.rsna.servlets.LoggerLevelServlet;
import org.rsna.servlets.LoginServlet;
import org.rsna.servlets.SysPropsServlet;
import org.rsna.servlets.UserManagerServlet;
import org.rsna.servlets.UserServlet;
import org.rsna.util.Cache;
import org.rsna.util.ClasspathUtil;
import org.rsna.util.FileUtil;
import org.rsna.util.HttpUtil;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * The ClinicalTrialProcessor program.
 */
public class ClinicalTrialProcessor {

	static final File libraries = new File("libraries");
	static final String mainClassName = "org.rsna.ctp.ClinicalTrialProcessor";
	static boolean running = true;
	static Logger logger = null;

	/**
	 * The main method of the ClinicalTrialProcessor program.
	 * This method just instantiates the main class.
	 * IMPORTANT: This approach requires that <u>all</u> the
	 * directories containing the libraries used by the program
	 * appear in the java.ext.dirs list. This is done by the
	 * CTP-startup program when it launches CTP. It is also done
	 * by the Windows service. The extensions must include the
	 * directory containing CTP.jar plus the CTP/libraries
	 * directory.
	 */
	public static void main(String[] args) {
		//Set the context classloader to allow dcm4che to load its classes
		Thread.currentThread().setContextClassLoader(ClinicalTrialProcessor.class.getClassLoader());

		//Instantiate the main class
		new ClinicalTrialProcessor();
	}

	/**
	 * The startup method of the ClinicalTrialProcessor program.
	 * This method is used when running CTP as a Windows service.
	 * It does not return until the stopService method is called
	 * independently by the service manager.
	 */
	public static void startService(String[] args) {
		System.out.println("Start [ServiceManager]");
		main(args);
		while (running) {
			try { Thread.sleep(2000); }
			catch (Exception ignore) { }
		}
		if (logger != null) logger.info("startService returned\n");
	}

	/**
	 * The shutdown method of the ClinicalTrialProcessor program.
	 * This method is used when running CTP as a Windows service.
	 * This method makes an HTTP connection to the ShutdownServlet
	 * to trigger the plugins and pipelines to close down gracefully.
	 */
	public static void stopService(String[] args) {
		try {
			Configuration config = Configuration.getInstance();
			boolean ssl = config.getServerSSL();
			int port = config.getServerPort();

			URL url = new URL( "http" + (ssl?"s":"") + "://127.0.0.1:" + port + "/shutdown" );
			HttpURLConnection conn = HttpUtil.getConnection(url);
			conn.setRequestMethod("GET");
			conn.setRequestProperty("servicemanager", "stayalive");
			conn.connect();

			String result = FileUtil.getText( conn.getInputStream() );
			if (result.contains("Goodbye.")) {
				System.out.println("Normal shutdown [ServiceManager]");
				running = false;
			}
			else System.out.println("Unable to service the shutdown request from ServiceManager.");
		}
		catch (Exception keepRunning) { keepRunning.printStackTrace(); }
		if (logger != null) logger.info("stopService returned");
	}

	/**
	 * The constructor of the ClinicalTrialProcessor program.
	 * There is no UI presented by the program. All access to
	 * the configuration and status of the program is presented
	 * through the HTTP server.
	 */
	public ClinicalTrialProcessor() {

		//Set up the classpath
		ClasspathUtil.addJARs( new File("libraries") );

		//Initialize Log4J
		File logs = new File("logs");
		logs.mkdirs();
		File logProps = new File("log4j.properties");
		PropertyConfigurator.configure(logProps.getAbsolutePath());
		logger = Logger.getLogger(ClinicalTrialProcessor.class);

		//Instantiate the singleton Cache and clear it.
		Cache.getInstance(new File("CACHE")).clear();

		//Get the configuration
		Configuration config = Configuration.getInstance();

		//Instantiate the singleton Users class
		Users users = Users.getInstance(config.getUsersClassName(), config.getServerElement());

		//Add the CTP roles
		String[] roles = { "read", "delete", "import", "qadmin", "guest", "proxy" };
		for (String role : roles) users.addRole(role);

		//Disable session timeouts for the server
		Authenticator.getInstance().setSessionTimeout( 0L );

		//Create the ServletSelector for the HttpServer
		ServletSelector selector =
				new ServletSelector(
						new File("ROOT"),
						config.getRequireAuthentication());

		//Add in the servlets
		selector.addServlet("login",		LoginServlet.class);
		selector.addServlet("users",		UserManagerServlet.class);
		selector.addServlet("user",			UserServlet.class);
		selector.addServlet("logs",			LogServlet.class);
		selector.addServlet("configuration",ConfigurationServlet.class);
		selector.addServlet("status",		StatusServlet.class);
		selector.addServlet("quarantines",	QuarantineServlet.class);
		selector.addServlet("idmap",		IDMapServlet.class);
		selector.addServlet("objecttracker",ObjectTrackerServlet.class);
		selector.addServlet("databaseverifier",DBVerifierServlet.class);
		selector.addServlet("decipher",		DecipherServlet.class);
		selector.addServlet("system",		SysPropsServlet.class);
		selector.addServlet("environment",	EnvironmentServlet.class);
		selector.addServlet("daconfig",		DicomAnonymizerServlet.class);
		selector.addServlet("script",		ScriptServlet.class);
		selector.addServlet("lookup",		LookupServlet.class);
		selector.addServlet("webstart",		ApplicationServer.class);
		selector.addServlet("level",		LoggerLevelServlet.class);
		selector.addServlet("shutdown",		ShutdownServlet.class);
		selector.addServlet("server",		ServerServlet.class);
		selector.addServlet("summary",		SummaryServlet.class);

		//Instantiate the server.
		int port = config.getServerPort();
		boolean ssl = config.getServerSSL();
		HttpServer httpServer = null;
		try { httpServer = new HttpServer(ssl, port, selector); }
		catch (Exception ex) {
			logger.error("Unable to instantiate the HTTP Server on port "+port, ex);
			System.exit(0);
		}

		//Start the system
		config.start(httpServer);
	}

}
