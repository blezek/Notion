package edu.mayo.qia.pacs.dicom;

import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.UID;
import org.dcm4che2.data.UIDDictionary;
import org.dcm4che2.data.VR;
import org.dcm4che2.net.Association;
import org.dcm4che2.net.CommandUtils;
import org.dcm4che2.net.ConfigurationException;
import org.dcm4che2.net.Device;
import org.dcm4che2.net.DimseRSP;
import org.dcm4che2.net.DimseRSPHandler;
import org.dcm4che2.net.ExtQueryTransferCapability;
import org.dcm4che2.net.ExtRetrieveTransferCapability;
import org.dcm4che2.net.NetworkApplicationEntity;
import org.dcm4che2.net.NetworkConnection;
import org.dcm4che2.net.NoPresentationContextException;
import org.dcm4che2.net.TransferCapability;
import org.dcm4che2.net.UserIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//package org.dcm4che2.tool.dcmmover;

import java.awt.event.ActionListener;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.Executor;

import edu.mayo.qia.pacs.Notion;

/**
 * Provides DICOM Query/Retrieve SCU capability for the DcmMover.
 * 
 * @author gpotter
 * @version $Revision$
 * @see org.dcm4che2.tool.DcmQR Based on original work by gunter
 *      zeilinger(gunterze@gmail.com). Original version - Revision: 4892 $
 *      $Date: 2007-08-21 09:36:10 +0200 (Tue, 21 Aug 2007)
 */
@SuppressWarnings("javadoc")
public class DcmQR {

  static Logger log = LoggerFactory.getLogger(DcmQR.class);
  public static final String[] ONLY_IVRLE_TS = { UID.ImplicitVRLittleEndian };

  private static final String STUDY_QR_LEVEL_STR = "STUDY";
  private static final String SERIES_QR_LEVEL_STR = "SERIES";

  private static final String[] STUDY_LEVEL_FIND_CUID = { UID.StudyRootQueryRetrieveInformationModelFIND, UID.PatientRootQueryRetrieveInformationModelFIND };

  private static final String[] STUDY_LEVEL_MOVE_CUID = { UID.StudyRootQueryRetrieveInformationModelMOVE, UID.PatientRootQueryRetrieveInformationModelMOVE };

  // NB: added StudyDescription for Notion's sake
  private static final int[] STUDY_RETURN_KEYS = { Tag.StudyDate, Tag.StudyTime, Tag.PatientID, Tag.PatientName, Tag.AccessionNumber, Tag.StudyID, Tag.StudyInstanceUID, Tag.NumberOfStudyRelatedSeries, Tag.NumberOfStudyRelatedInstances,
      Tag.StudyDescription };

  private static final int[] MOVE_KEYS = { Tag.QueryRetrieveLevel, Tag.PatientID, Tag.StudyInstanceUID, Tag.SeriesInstanceUID, Tag.SOPInstanceUID, };

  private static final String[] NATIVE_LE_TS = { UID.ImplicitVRLittleEndian, UID.ExplicitVRLittleEndian };

  // DJB, replace with Notion's thread pool
  // private Executor executor = new NewThreadExecutor("DCMQR");
  private Executor executor = Notion.context.getBean("executor", Executor.class);

  private NetworkApplicationEntity remoteAE = new NetworkApplicationEntity();

  private NetworkConnection remoteConn = new NetworkConnection();

  private Device device = new Device("DCMQR");

  private NetworkApplicationEntity ae = new NetworkApplicationEntity();

  private NetworkConnection conn = new NetworkConnection();

  private Association assoc;

  private int priority = 0;

  private String moveDest;

  private DicomObject keys = new BasicDicomObject();

  private int cancelAfter = Integer.MAX_VALUE;

  private int seriesFound;

  private int objectsFound;

  private int completed;

  private int warning;

  private int failed;

  private String moveError;

  private int moveStatus;

  private boolean relationQR;

  private boolean dateTimeMatching;

  private boolean fuzzySemanticPersonNameMatching;

  private boolean noExtNegotiation;

  // DJB, replace with Notion's thread pool
  // Executor eventExecutor = new NewThreadExecutor("DCMQR_EVENTS");
  Executor eventExecutor = Notion.context.getBean("executor", Executor.class);

ActionListener moveActionListener;

  public DcmQR() {
    this("DCMQR");
  }

  public DcmQR(String deviceName) {
    remoteAE.setInstalled(true);
    remoteAE.setAssociationAcceptor(true);
    remoteAE.setNetworkConnection(new NetworkConnection[] { remoteConn });

    device = new Device(deviceName);
    device.setNetworkApplicationEntity(ae);
    device.setNetworkConnection(conn);
    ae.setNetworkConnection(conn);
    ae.setAssociationInitiator(true);
    configureTransferCapability(false);

    setNoExtNegotiation(true);

    keys.putString(Tag.QueryRetrieveLevel, VR.CS, STUDY_QR_LEVEL_STR);
    int[] tags = STUDY_RETURN_KEYS;
    for (int i = 0; i < tags.length; i++)
      keys.putNull(tags[i], null);
  }

  public final String getRemoteAE() {
    return remoteAE.getAETitle();
  }

  public final void setLocalHost(String hostname) {
    conn.setHostname(hostname);
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

  public void setMoveDest(String aet) {
    moveDest = aet;
  }

  public final void setUserIdentity(UserIdentity userIdentity) {
    ae.setUserIdentity(userIdentity);
  }

  public final void setPriority(int priority) {
    this.priority = priority;
  }

  public final void setConnectTimeout(int connectTimeout) {
    conn.setConnectTimeout(connectTimeout);
  }

  public final void setMaxPDULengthReceive(int maxPDULength) {
    ae.setMaxPDULengthReceive(maxPDULength);
  }

  public final void setMaxOpsInvoked(int maxOpsInvoked) {
    ae.setMaxOpsInvoked(maxOpsInvoked);
  }

  public final void setPackPDV(boolean packPDV) {
    ae.setPackPDV(packPDV);
  }

  public final void setAssociationReaperPeriod(int period) {
    device.setAssociationReaperPeriod(period);
  }

  public final void setDimseRspTimeout(int timeout) {
    ae.setDimseRspTimeout(timeout);
  }

  public final void setTcpNoDelay(boolean tcpNoDelay) {
    conn.setTcpNoDelay(tcpNoDelay);
  }

  public final void setAcceptTimeout(int timeout) {
    conn.setAcceptTimeout(timeout);
  }

  public final void setReleaseTimeout(int timeout) {
    conn.setReleaseTimeout(timeout);
  }

  public final void setSocketCloseDelay(int timeout) {
    conn.setSocketCloseDelay(timeout);
  }

  public final void setMaxPDULengthSend(int maxPDULength) {
    ae.setMaxPDULengthSend(maxPDULength);
  }

  public final void setReceiveBufferSize(int bufferSize) {
    conn.setReceiveBufferSize(bufferSize);
  }

  public final void setSendBufferSize(int bufferSize) {
    conn.setSendBufferSize(bufferSize);
  }

  public void setNoExtNegotiation(boolean b) {
    this.noExtNegotiation = b;
  }

  public void setSemanticPersonNameMatching(boolean b) {
    this.fuzzySemanticPersonNameMatching = b;
  }

  public void setDateTimeMatching(boolean b) {
    this.dateTimeMatching = b;
  }

  public void setRelationQR(boolean b) {
    this.relationQR = b;
  }

  public final int getFailed() {
    return failed;
  }

  public final int getWarning() {
    return warning;
  }

  public final int getTotalFound() {
    return objectsFound;
  }

  public final int getSeriesFound() {
    return seriesFound;
  }

  public final int getTotalRetrieved() {
    return completed + warning;
  }

  public void setCancelAfter(int limit) {
    this.cancelAfter = limit;
  }

  public void addMatchingKey(int[] tagPath, String value) {
    keys.putString(tagPath, null, value);
  }

  private void reset() {
    seriesFound = 0;
    objectsFound = 0;
    completed = 0;
    warning = 0;
    failed = 0;
    moveStatus = 0;
    moveError = "";
  }

  public void abort() {
    final String fn = "abort: ";

    log.error(fn + "Aborting Query/Retrieve processing");
    assoc.abort();
  }

  public Exception getAssocException() {
    final String fn = "getAssocException: ";

    Method assocGetException = null;
    Exception assocE = null;

    try {
      assocGetException = assoc.getClass().getDeclaredMethod("getException", new Class[0]);
      assocGetException.setAccessible(true);
    } catch (Exception e) {
      log.error(fn + "Failed to check for association exception.", e);
      return e;
    }

    try {
      assocE = (Exception) assocGetException.invoke(assoc, new Object[0]);
    } catch (Exception e) {
      log.error(fn + "Failed to check for association exception.", e);
      return e;
    }

    return assocE;
  }

  public void qrStudy(String studyUid) throws DcmMoveException {
    qrStudy(studyUid, null);
  }

  public void qrStudy(String studyUid, String seriesUID) throws DcmMoveException {
    final String fn = "qrStudy: ";

    if (seriesUID == null) {
      log.info(fn + "Moving study with uid " + studyUid);
    } else {
      log.info(fn + "Moving series " + seriesUID + " from study " + studyUid);
    }

    reset();

    // Query by study uid only
    addMatchingKey(Tag.toTagPath("StudyInstanceUID"), studyUid);
    if (seriesUID != null) {
      keys.putString(Tag.QueryRetrieveLevel, VR.CS, SERIES_QR_LEVEL_STR);
      keys.putNull(Tag.NumberOfSeriesRelatedInstances, VR.IS);
      addMatchingKey(Tag.toTagPath("SeriesInstanceUID"), seriesUID);
    }

    long t1 = System.currentTimeMillis();
    try {
      // Open association
      log.debug(fn + "Opening a Q/R SCU association...");
      open();
    } catch (Exception e) {
      log.error(fn + "Failed to establish association:", e);
      throw new DcmMoveException("Q/R SCU failed to establish the association.", e);
    }
    long t2 = System.currentTimeMillis();
    log.info(fn + "Connected to " + getRemoteAE() + " in " + ((t2 - t1) / 1000F) + "s");

    try {

      // Do C-FIND
      log.debug(fn + "Executing the study query...");
      DicomObject result = query();
      if (null != result) {
        long t3 = System.currentTimeMillis();
        log.info(fn + "Found requested study in " + ((t3 - t2) / 1000F) + "s");
        //
        // Do C-MOVE
        //
        log.debug(fn + "Executing the study retrieve...");
        retrieve(result);
        long t4 = System.currentTimeMillis();
        log.info(fn + "Retrieved " + getTotalRetrieved() + " objects (warning: " + getWarning() + ", failed: " + getFailed() + ") in " + ((t4 - t3) / 1000F) + "s");
      } else {
        log.info(fn + "No results returned from study query");
      }

    } catch (IOException e) {
      log.error(fn + "Exception ocurred while performing the Query/Retrieve:", e);
      throw new DcmMoveException("Exception ocurred while performing the Query/Retrieve.", e);
    } catch (InterruptedException e) {
      log.error(fn + "Exception ocurred while performing the Query/Retrieve:", e);
      throw new DcmMoveException("Exception ocurred while performing the Query/Retrieve.", e);
    } finally {
      try {
        log.debug(fn + "Closing the Q/R SCU association...");
        close();
      } catch (InterruptedException e) {
        log.error(fn + "Exception ocurred while closing the association:", e);
      }
      log.debug("Released connection to " + getRemoteAE());
    }
    log.debug(fn + "Study Query/Retrieve completed");
  }

  private void configureTransferCapability(boolean ivrle) {
    String[] findcuids = STUDY_LEVEL_FIND_CUID;
    String[] movecuids = STUDY_LEVEL_MOVE_CUID;
    TransferCapability[] tc = new TransferCapability[findcuids.length + movecuids.length];
    int i = 0;
    for (int j = 0; j < findcuids.length; j++)
      tc[i++] = mkFindTC(findcuids[j], ivrle ? ONLY_IVRLE_TS : NATIVE_LE_TS);
    for (int j = 0; j < movecuids.length; j++)
      tc[i++] = mkMoveTC(movecuids[j], ivrle ? ONLY_IVRLE_TS : NATIVE_LE_TS);
    ae.setTransferCapability(tc);
  }

  private TransferCapability mkMoveTC(String cuid, String[] ts) {
    ExtRetrieveTransferCapability tc = new ExtRetrieveTransferCapability(cuid, ts, TransferCapability.SCU);
    tc.setExtInfoBoolean(ExtRetrieveTransferCapability.RELATIONAL_RETRIEVAL, relationQR);
    if (noExtNegotiation)
      tc.setExtInfo(null);
    return tc;
  }

  private TransferCapability mkFindTC(String cuid, String[] ts) {
    ExtQueryTransferCapability tc = new ExtQueryTransferCapability(cuid, ts, TransferCapability.SCU);
    tc.setExtInfoBoolean(ExtQueryTransferCapability.RELATIONAL_QUERIES, relationQR);
    tc.setExtInfoBoolean(ExtQueryTransferCapability.DATE_TIME_MATCHING, dateTimeMatching);
    tc.setExtInfoBoolean(ExtQueryTransferCapability.FUZZY_SEMANTIC_PN_MATCHING, fuzzySemanticPersonNameMatching);
    if (noExtNegotiation)
      tc.setExtInfo(null);
    return tc;
  }

  public void open() throws IOException, ConfigurationException, InterruptedException {
    assoc = ae.connect(remoteAE, executor);
  }

  // Query for one specific study
  public DimseRSP queryAll() throws IOException, InterruptedException, DcmMoveException {
    final String fn = "query: ";

    TransferCapability tc = selectFindTransferCapability();
    String cuid = tc.getSopClass();
    String tsuid = selectTransferSyntax(tc);

    if (log.isDebugEnabled()) {
      log.debug(fn + "Send Query Request using " + UIDDictionary.getDictionary().prompt(cuid) + ":\n" + keys.toString());
    }

    DimseRSP rsp = assoc.cfind(cuid, priority, keys, tsuid, cancelAfter);
    return rsp;
  }

  // Query for one specific study
  public DicomObject query() throws IOException, InterruptedException, DcmMoveException {
    final String fn = "query: ";

    TransferCapability tc = selectFindTransferCapability();
    String cuid = tc.getSopClass();
    String tsuid = selectTransferSyntax(tc);

    if (log.isDebugEnabled()) {
      log.debug(fn + "Send Query Request using " + UIDDictionary.getDictionary().prompt(cuid) + ":\n" + keys.toString());
    }

    DimseRSP rsp = assoc.cfind(cuid, priority, keys, tsuid, cancelAfter);

    DicomObject data = null;
    if (rsp.next()) {
      DicomObject cmd = rsp.getCommand();
      if (CommandUtils.isPending(cmd)) {
        // This is the study we are looking for
        data = rsp.getDataset();
        if (data.contains(Tag.SeriesInstanceUID)) {
          objectsFound = data.getInt(Tag.NumberOfSeriesRelatedInstances);
        } else {
          objectsFound = data.getInt(Tag.NumberOfStudyRelatedInstances);
        }
        seriesFound = data.getInt(Tag.NumberOfStudyRelatedSeries);
        log.debug(fn + "Received Query Response: \n" + data.toString());

        // There should be one more response objects indicating success, but
        // i just ignore it at this level
      } else {
        log.info(fn + "No data returned! The Query/Retrieve SCP returned a status of '" + cmd.getInt(Tag.Status) + "'");
      }
    }

    Exception e = getAssocException();
    if (e != null) {
      log.error(fn + "Exception ocurred on the association while querying:", e);
      throw new DcmMoveException("Exception ocurred on the association while querying.", e);
    }

    return data;
  }

  private TransferCapability selectFindTransferCapability() throws NoPresentationContextException {
    TransferCapability tc = selectTransferCapability(STUDY_LEVEL_FIND_CUID);
    if (tc != null)
      return tc;
    throw new NoPresentationContextException(UIDDictionary.getDictionary().prompt(STUDY_LEVEL_FIND_CUID[0]) + " not supported by" + remoteAE.getAETitle());
  }

  private String selectTransferSyntax(TransferCapability tc) {
    String[] tcuids = tc.getTransferSyntax();
    if (Arrays.asList(tcuids).indexOf(UID.DeflatedExplicitVRLittleEndian) != -1)
      return UID.DeflatedExplicitVRLittleEndian;
    return tcuids[0];
  }

  private void retrieve(DicomObject findResult) throws IOException, InterruptedException, DcmMoveException {
    final String fn = "retrieve: ";

    if (moveDest == null)
      throw new IllegalStateException("moveDest == null");

    TransferCapability tc = selectTransferCapability(STUDY_LEVEL_MOVE_CUID);
    if (tc == null)
      throw new NoPresentationContextException(UIDDictionary.getDictionary().prompt(STUDY_LEVEL_MOVE_CUID[0]) + " not supported by" + remoteAE.getAETitle());

    String cuid = tc.getSopClass();
    String tsuid = selectTransferSyntax(tc);
    DicomObject keys = findResult.subSet(MOVE_KEYS);
    log.info(fn + "Send Retrieve Request using class '" + UIDDictionary.getDictionary().prompt(cuid) + "' and transfer syntax '" + UIDDictionary.getDictionary().prompt(tsuid) + "':\n" + keys.toString());
    DimseRSPHandler rspHandler = new DimseRSPHandler() {
      @Override
      public void onDimseRSP(Association as, DicomObject cmd, DicomObject data) {
        DcmQR.this.onMoveRSP(as, cmd, data);
      }
    };
    assoc.cmove(cuid, priority, keys, tsuid, moveDest, rspHandler);

    assoc.waitForDimseRSP();

    // Do some move error checking
    Exception e = getAssocException();
    if (e != null) {
      log.error(fn + "Exception ocurred on the association while retrieving:", e);
      throw new DcmMoveException("Exception ocurred on the association while retrieving.", e);
    } else if (isAbortingMove(moveStatus)) {
      StringBuffer str = new StringBuffer("Query/Retrieve SCP terminated move prematurally. Move status = ");
      str.append(Integer.toHexString(moveStatus));
      if (moveError != null) {
        str.append(", Move error = " + moveError);
      }
      log.error(fn + str.toString());
      throw new DcmMoveException(str.toString());
    }
  }

  protected void onMoveRSP(Association as, DicomObject cmd, DicomObject data) {
    try {
      log.debug("onMoveRSP(" + (as == null ? "(null)" : as.toString()) + "," + (cmd == null ? "(null)" : cmd.toString()) + "," + (data == null ? "(null)" : data.toString()) + ")");
      // Set these values directly
      completed = cmd.getInt(Tag.NumberOfCompletedSuboperations);
      warning = cmd.getInt(Tag.NumberOfWarningSuboperations);
      failed = cmd.getInt(Tag.NumberOfFailedSuboperations);
    } catch (Exception e) {

    }
    // if (!CommandUtils.isPending(cmd)) {
    {
      moveStatus = cmd.getInt(Tag.Status);
      // if (isAbortingMove(moveStatus)) {
      // checkError(cmd);
      // } else {
      // completed += cmd.getInt(Tag.NumberOfCompletedSuboperations);
      // warning += cmd.getInt(Tag.NumberOfWarningSuboperations);
      // failed += cmd.getInt(Tag.NumberOfFailedSuboperations);
      // }

    }
  }

  // Check the move status for an abort
  protected boolean isAbortingMove(int moveStatus) {
    switch (moveStatus) {
    case 0xa701:
    case 0xa702:
    case 0xa801:
    case 0xa900:
    case 0xc002:
    case 0xfe00:
    case 0xb000:
      return true;
    case 0xff00:
    case 0xff01:
    case 0x0000:
      return false;
    default:
      return true;
    }
  }

  protected void checkError(DicomObject moveRspCmd) {
    String error = moveRspCmd.getString(Tag.ErrorComment);
    if (null != error) {
      moveError = error;
    }
  }

  private TransferCapability selectTransferCapability(String[] cuid) {
    TransferCapability tc;
    for (int i = 0; i < cuid.length; i++) {
      tc = assoc.getTransferCapabilityAsSCU(cuid[i]);
      if (tc != null)
        return tc;
    }
    return null;
  }

  public void close() throws InterruptedException {
    assoc.release(true);
  }

  public void logConfiguration(StringBuffer str) {
    str.append("\n\tCalled AET: " + remoteAE.getAETitle());
    str.append("\n\tCalling AET: " + ae.getAETitle());
    str.append("\n\tHostname: " + conn.getHostname());
    str.append("\n\tPort: " + conn.getPort());
    str.append("\n\tRemote Hostname: " + remoteConn.getHostname());
    str.append("\n\tRemote Port: " + remoteConn.getPort());
    str.append("\n\tMove Priority: " + priority);
    str.append("\n\tNo Extended Negotiation: " + noExtNegotiation);
    str.append("\n\tMove Response Timeout: " + ae.getRetrieveRspTimeout());
    str.append("\n\tIdle Timeout: " + ae.getIdleTimeout());
    str.append("\n\tTransfer Capability:");
    for (TransferCapability tc : ae.getTransferCapability()) {
      String sopClassName = UIDDictionary.getDictionary().nameOf(tc.getSopClass());
      str.append("\n\t\tSOP Class[" + sopClassName + "]  Transfer Syntaxes:");
      for (String xferSyntaxUid : tc.getTransferSyntax()) {
        str.append("\n\t\t\t" + UIDDictionary.getDictionary().nameOf(xferSyntaxUid));
      }
    }
  }

  public void addMoveActionListener(ActionListener al) {
    moveActionListener = al;
  }

}