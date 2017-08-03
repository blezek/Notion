/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ctp.stdstages;

import org.apache.log4j.Logger;
import org.rsna.ctp.objects.FileObject;
import org.rsna.ctp.objects.XmlObject;
import org.rsna.ctp.pipeline.AbstractPipelineStage;
import org.rsna.ctp.pipeline.Processor;
import org.rsna.util.FileUtil;
import org.w3c.dom.Element;

import java.io.File;

/**
 * A script-based filter for XmlObjects.
 */
public class XmlFilter extends AbstractPipelineStage implements Processor, Scriptable {

	static final Logger logger = Logger.getLogger(XmlFilter.class);

	public File scriptFile = null;

	/**
	 * Construct the XmlFilter PipelineStage.
	 * @param element the XML element from the configuration file
	 * specifying the configuration of the stage.
	 */
	public XmlFilter(Element element) {
		super(element);
		scriptFile = FileUtil.getFile(element.getAttribute("script").trim(), "examples/example-filter.script");
	}

	/**
	 * Evaluate the script for the object and quarantine the object if
	 * the result is false.
	 * @param fileObject the object to process.
	 * @return the same FileObject if the result is true; otherwise null.
	 */
	@Override
  public FileObject process(FileObject fileObject) {
		lastFileIn = new File(fileObject.getFile().getAbsolutePath());
		lastTimeIn = System.currentTimeMillis();

		if (fileObject instanceof XmlObject) {
			if (!((XmlObject)fileObject).matches(scriptFile)) {
				if (quarantine != null) quarantine.insert(fileObject);
				lastFileOut = null;
				lastTimeOut = System.currentTimeMillis();
				return null;
			}
		}
		lastFileOut = new File(fileObject.getFile().getAbsolutePath());
		lastTimeOut = System.currentTimeMillis();
		return fileObject;
	}

	/**
	 * Get the script file.
	 * @return the script file used by this stage.
	 */
	@Override
  public File[] getScriptFiles() {
		return new File[] {scriptFile};
	}
}