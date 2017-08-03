/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at http://sourceforge.net/projects/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Gunter Zeilinger, Huetteldorferstr. 24/10, 1150 Vienna/Austria/Europe.
 * Portions created by the Initial Developer are Copyright (C) 2002-2005
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * Gunter Zeilinger <gunterze@gmail.com>
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */
package edu.mayo.qia.pacs.dicom;

import org.apache.log4j.Logger;
import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.UID;
import org.dcm4che2.data.UIDDictionary;
import org.dcm4che2.data.VR;
import org.dcm4che2.io.DicomInputStream;
import org.dcm4che2.io.DicomOutputStream;
import org.dcm4che2.io.StopTagInputHandler;
import org.dcm4che2.io.TranscoderInputHandler;
import org.dcm4che2.net.Association;
import org.dcm4che2.net.ConfigurationException;
import org.dcm4che2.net.Device;
import org.dcm4che2.net.DimseRSPHandler;
import org.dcm4che2.net.NetworkApplicationEntity;
import org.dcm4che2.net.NetworkConnection;
import org.dcm4che2.net.NoPresentationContextException;
import org.dcm4che2.net.PDVOutputStream;
import org.dcm4che2.net.TransferCapability;
import org.dcm4che2.util.CloseUtils;
import org.dcm4che2.util.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import edu.mayo.qia.pacs.Notion;

@SuppressWarnings("javadoc")
public class DcmSnd {
  static Logger logger = Logger.getLogger(DcmSnd.class);
  private static final int PEEK_LEN = 1024;

  private static final String[] IVLE_TS = { UID.ImplicitVRLittleEndian, UID.ExplicitVRLittleEndian, UID.ExplicitVRBigEndian, };

  private static final String[] EVLE_TS = { UID.ExplicitVRLittleEndian, UID.ImplicitVRLittleEndian, UID.ExplicitVRBigEndian, };

  private static final String[] EVBE_TS = { UID.ExplicitVRBigEndian, UID.ExplicitVRLittleEndian, UID.ImplicitVRLittleEndian, };

  private final Map<String, Set<String>> as2ts = new HashMap<String, Set<String>>();

  /** TransferSyntax: DCM4CHE URI Referenced */
  private static final String DCM4CHEE_URI_REFERENCED_TS_UID = "1.2.40.0.13.1.1.2.4.94";

  private final Executor executor;

  private final NetworkApplicationEntity remoteAE = new NetworkApplicationEntity();

  private final NetworkConnection remoteConn = new NetworkConnection();

  private final Device device;

  private final NetworkApplicationEntity ae = new NetworkApplicationEntity();

  private final NetworkConnection conn = new NetworkConnection();

  private final ArrayList<FileInfo> files = new ArrayList<FileInfo>();

  private Association assoc;

  private int priority = 0;

  private int transcoderBufferSize = 1024;

   int filesSent = 0;

  long totalSize = 0L;

  private boolean fileref = false;

  boolean stgcmt = false;

  long shutdownDelay = 1000L;

  DicomObject stgCmtResult;

  private DicomObject coerceAttrs;

  private String[] suffixUID;

  public DcmSnd(String name) {
    device = new Device(name);
    // DJB, replaced with Notion's new NewThreadExecutor(name);
    executor = Notion.context.getBean("executor", Executor.class);
    remoteAE.setInstalled(true);
    remoteAE.setAssociationAcceptor(true);
    remoteAE.setNetworkConnection(new NetworkConnection[] { remoteConn });

    device.setNetworkApplicationEntity(ae);
    device.setNetworkConnection(conn);
    ae.setNetworkConnection(conn);
    ae.setAssociationInitiator(true);
    ae.setAssociationAcceptor(true);
    ae.setAETitle(name);
  }

  public final void setLocalHost(String hostname) {
    conn.setHostname(hostname);
  }

  public final void setLocalPort(int port) {
    conn.setPort(port);
  }

  public final void setRemoteHost(String hostname) {
    remoteConn.setHostname(hostname);
  }

  public final void setRemotePort(int port) {
    remoteConn.setPort(port);
  }

  public final void setCalledAET(String called) {
    remoteAE.setAETitle(called);
  }

  public final void setCalling(String calling) {
    ae.setAETitle(calling);
  }

  public final void setShutdownDelay(int shutdownDelay) {
    this.shutdownDelay = shutdownDelay;
  }

  public final void setConnectTimeout(int connectTimeout) {
    conn.setConnectTimeout(connectTimeout);
  }

  public final void setTcpNoDelay(boolean tcpNoDelay) {
    conn.setTcpNoDelay(tcpNoDelay);
  }

  public void addFile(File f) {
    if (f.isDirectory()) {
      File[] fs = f.listFiles();
      if (fs == null || fs.length == 0)
        return;
      for (int i = 0; i < fs.length; i++)
        addFile(fs[i]);
      return;
    }

    if (f.isHidden())
      return;

    FileInfo info = new FileInfo(f);
    DicomObject dcmObj = new BasicDicomObject();
    DicomInputStream in = null;
    try {
      in = new DicomInputStream(f);
      in.setHandler(new StopTagInputHandler(Tag.StudyDate));
      in.readDicomObject(dcmObj, PEEK_LEN);
      info.tsuid = in.getTransferSyntax().uid();
      info.fmiEndPos = in.getEndOfFileMetaInfoPosition();
    } catch (IOException e) {
      logger.error("WARNING: Failed to parse " + f + " - skipped.", e);
      return;
    } finally {
      CloseUtils.safeClose(in);
    }
    info.cuid = dcmObj.getString(Tag.MediaStorageSOPClassUID, dcmObj.getString(Tag.SOPClassUID));
    if (info.cuid == null) {
      logger.warn("WARNING: Missing SOP Class UID in " + f + " - skipped.");
      return;
    }
    info.iuid = dcmObj.getString(Tag.MediaStorageSOPInstanceUID, dcmObj.getString(Tag.SOPInstanceUID));
    if (info.iuid == null) {
      logger.warn("WARNING: Missing SOP Instance UID in " + f + " - skipped.");
      return;
    }
    if (suffixUID != null)
      info.iuid = info.iuid + suffixUID[0];
    addTransferCapability(info.cuid, info.tsuid);
    files.add(info);
  }

  public void addTransferCapability(String cuid, String tsuid) {
    Set<String> ts = as2ts.get(cuid);
    if (fileref) {
      if (ts == null) {
        as2ts.put(cuid, Collections.singleton(DCM4CHEE_URI_REFERENCED_TS_UID));
      }
    } else {
      if (ts == null) {
        ts = new HashSet<String>();
        ts.add(UID.ImplicitVRLittleEndian);
        as2ts.put(cuid, ts);
      }
      ts.add(tsuid);
    }
  }

  public void configureTransferCapability() {
    TransferCapability[] tc = new TransferCapability[as2ts.size()];
    Iterator<Map.Entry<String, Set<String>>> iter = as2ts.entrySet().iterator();
    for (int i = 0; i < tc.length; i++) {
      Map.Entry<String, Set<String>> e = iter.next();
      String cuid = e.getKey();
      Set<String> ts = e.getValue();
      tc[i] = new TransferCapability(cuid, ts.toArray(new String[ts.size()]), TransferCapability.SCU);
    }
    ae.setTransferCapability(tc);
  }

  public void open() throws IOException, ConfigurationException, InterruptedException {
    assoc = ae.connect(remoteAE, executor);
  }

  public void send(FileMovedHandler callback) {

    for (int i = 0, n = files.size(); i < n; ++i) {
      FileInfo info = files.get(i);
      TransferCapability tc = assoc.getTransferCapabilityAsSCU(info.cuid);
      if (tc == null) {
        logger.error(UIDDictionary.getDictionary().prompt(info.cuid) + " not supported by " + remoteAE.getAETitle());
        logger.error("skip file " + info.f);
        continue;
      }
      String tsuid = selectTransferSyntax(tc.getTransferSyntax(), fileref ? DCM4CHEE_URI_REFERENCED_TS_UID : info.tsuid);
      if (tsuid == null) {
        logger.error(UIDDictionary.getDictionary().prompt(info.cuid) + " with " + UIDDictionary.getDictionary().prompt(fileref ? DCM4CHEE_URI_REFERENCED_TS_UID : info.tsuid) + " not supported by " + remoteAE.getAETitle());
        logger.error("skip file " + info.f);
        continue;
      }
      try {
        DimseRSPHandler rspHandler = new DimseRSPHandler() {
          @Override
          public void onDimseRSP(Association as, DicomObject cmd, DicomObject data) {
            DcmSnd.this.onDimseRSP(cmd);
          }
        };

        assoc.cstore(info.cuid, info.iuid, priority, new DataWriter(info), tsuid, rspHandler);
        if (callback != null) {
          callback.fileMoved(i, n);
        }
      } catch (NoPresentationContextException e) {
        logger.error("WARNING: " + e.getMessage() + " - cannot send " + info.f);
      } catch (IOException e) {
        logger.error("ERROR: Failed to send - " + info.f + ": " + e.getMessage(), e);
      } catch (InterruptedException e) {
        // should not happen
        logger.error("Interupted", e);
      }
    }
    try {
      assoc.waitForDimseRSP();
    } catch (InterruptedException e) {
      // should not happen
      e.printStackTrace();
    }
  }

  private String selectTransferSyntax(String[] available, String tsuid) {
    if (tsuid.equals(UID.ImplicitVRLittleEndian))
      return selectTransferSyntax(available, IVLE_TS);
    if (tsuid.equals(UID.ExplicitVRLittleEndian))
      return selectTransferSyntax(available, EVLE_TS);
    if (tsuid.equals(UID.ExplicitVRBigEndian))
      return selectTransferSyntax(available, EVBE_TS);
    for (int j = 0; j < available.length; j++)
      if (available[j].equals(tsuid))
        return tsuid;
    return null;
  }

  private String selectTransferSyntax(String[] available, String[] tsuids) {
    for (int i = 0; i < tsuids.length; i++)
      for (int j = 0; j < available.length; j++)
        if (available[j].equals(tsuids[i]))
          return available[j];
    return null;
  }

  public void close() {
    try {
      assoc.release(false);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  public static final class FileInfo {
    File f;

    String cuid;

    String iuid;

    String tsuid;

    long fmiEndPos;

    long length;

    boolean transferred;

    int status;

    public FileInfo(File f) {
      this.f = f;
      this.length = f.length();
    }

  }

  private class DataWriter implements org.dcm4che2.net.DataWriter {

    private FileInfo info;

    public DataWriter(FileInfo info) {
      this.info = info;
    }

    @Override
    public void writeTo(PDVOutputStream out, String tsuid) throws IOException {
      if (coerceAttrs != null || suffixUID != null) {
        DicomObject attrs;
        DicomInputStream dis = new DicomInputStream(info.f);
        try {
          dis.setHandler(new StopTagInputHandler(Tag.PixelData));
          attrs = dis.readDicomObject();
          suffixUIDs(attrs);
          coerceAttrs(attrs);
          DicomOutputStream dos = new DicomOutputStream(out);
          dos.writeDataset(attrs, tsuid);
          if (dis.tag() >= Tag.PixelData) {
            copyPixelData(dis, dos, out);
            // copy attrs after PixelData
            dis.setHandler(dis);
            attrs = dis.readDicomObject();
            dos.writeDataset(attrs, tsuid);
          }
          dos.close();
        } finally {
          dis.close();
        }
      } else if (tsuid.equals(info.tsuid)) {
        FileInputStream fis = new FileInputStream(info.f);
        try {
          long skip = info.fmiEndPos;
          while (skip > 0)
            skip -= fis.skip(skip);
          out.copyFrom(fis);
        } finally {
          fis.close();
        }
      } else if (tsuid.equals(DCM4CHEE_URI_REFERENCED_TS_UID)) {
        DicomObject attrs;
        DicomInputStream dis = new DicomInputStream(info.f);
        try {
          dis.setHandler(new StopTagInputHandler(Tag.PixelData));
          attrs = dis.readDicomObject();
        } finally {
          dis.close();
        }
        DicomOutputStream dos = new DicomOutputStream(out);
        try {
          attrs.putString(Tag.RetrieveURI, VR.UT, info.f.toURI().toString());
          dos.writeDataset(attrs, tsuid);
        } finally {
          dos.close();
        }
      } else {
        DicomInputStream dis = new DicomInputStream(info.f);
        try {
          DicomOutputStream dos = new DicomOutputStream(out);
          dos.setTransferSyntax(tsuid);
          TranscoderInputHandler h = new TranscoderInputHandler(dos, transcoderBufferSize);
          dis.setHandler(h);
          dis.readDicomObject();
          dos.close();
        } finally {
          dis.close();
        }
      }
    }

  }

  private void suffixUIDs(DicomObject attrs) {
    if (suffixUID != null) {
      int[] uidTags = { Tag.SOPInstanceUID, Tag.SeriesInstanceUID, Tag.StudyInstanceUID };
      for (int i = 0; i < suffixUID.length; i++)
        attrs.putString(uidTags[i], VR.UI, attrs.getString(uidTags[i]) + suffixUID[i]);
    }
  }

  private void coerceAttrs(DicomObject attrs) {
    if (coerceAttrs != null)
      coerceAttrs.copyTo(attrs);
  }

  private void copyPixelData(DicomInputStream dis, DicomOutputStream dos, PDVOutputStream out) throws IOException {
    int vallen = dis.valueLength();
    dos.writeHeader(dis.tag(), dis.vr(), vallen);
    if (vallen == -1) {
      int tag;
      do {
        tag = dis.readHeader();
        vallen = dis.valueLength();
        dos.writeHeader(tag, null, vallen);
        out.copyFrom(dis, vallen);
      } while (tag == Tag.Item);
    } else {
      out.copyFrom(dis, vallen);
    }
  }

  private void promptErrRSP(String prefix, int status, FileInfo info, DicomObject cmd) {
    logger.error(prefix + StringUtils.shortToHex(status) + "H for " + info.f + ", cuid=" + info.cuid + ", tsuid=" + info.tsuid);
    logger.error(cmd.toString());
  }

  private void onDimseRSP(DicomObject cmd) {
    int status = cmd.getInt(Tag.Status);
    int msgId = cmd.getInt(Tag.MessageIDBeingRespondedTo);
    FileInfo info = files.get(msgId - 1);
    info.status = status;
    switch (status) {
    case 0:
      info.transferred = true;
      totalSize += info.length;
      ++filesSent;
      break;
    case 0xB000:
    case 0xB006:
    case 0xB007:
      info.transferred = true;
      totalSize += info.length;
      ++filesSent;
      promptErrRSP("WARNING: Received RSP with Status ", status, info, cmd);
      break;
    default:
      promptErrRSP("ERROR: Received RSP with Status ", status, info, cmd);
    }
  }

  protected synchronized void onNEventReportRSP(Association as, int pcid, DicomObject rq, DicomObject info, DicomObject rsp) {
    stgCmtResult = info;
    notifyAll();
  }

}
