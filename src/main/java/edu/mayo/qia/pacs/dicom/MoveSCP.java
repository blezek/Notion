package edu.mayo.qia.pacs.dicom;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.UID;
import org.dcm4che2.data.VR;
import org.dcm4che2.net.Association;
import org.dcm4che2.net.CommandUtils;
import org.dcm4che2.net.DicomServiceException;
import org.dcm4che2.net.Status;
import org.dcm4che2.net.service.CMoveSCP;
import org.dcm4che2.net.service.DicomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import edu.mayo.qia.pacs.Audit;
import edu.mayo.qia.pacs.Notion;
import edu.mayo.qia.pacs.components.Device;
import edu.mayo.qia.pacs.components.PoolManager;
import edu.mayo.qia.pacs.dicom.DICOMReceiver.AssociationInfo;
import edu.mayo.qia.pacs.metric.RateGauge;

@Component
public class MoveSCP extends DicomService implements CMoveSCP {
  static Logger logger = LoggerFactory.getLogger(MoveSCP.class);
  static Meter imageMeter = Notion.metrics.meter(MetricRegistry.name("DICOM", "image", "sent"));
  static Counter imageQueueCounter = Notion.metrics.counter(MetricRegistry.name("DICOM", "image", "send", "queue"));
  static Counter imageSentCounter = Notion.metrics.counter("DICOM.image.sent.count");
  static RateGauge imagesPerSecond;

  static public String[] PresentationContexts = new String[] { UID.StudyRootQueryRetrieveInformationModelMOVE, UID.PatientRootQueryRetrieveInformationModelMOVE };

  @Autowired
  JdbcTemplate template;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  PoolManager poolManager;

  public MoveSCP() {
    super(PresentationContexts);
    imagesPerSecond = new RateGauge();
    Notion.metrics.register("DICOM.image.sent.rate", imagesPerSecond);
  }

  @Override
  public void cmove(final Association as, final int pcid, final DicomObject command, DicomObject request) throws DicomServiceException, IOException {

    DICOMReceiver dicomReceiver = Notion.context.getBean("dicomReceiver", DICOMReceiver.class);
    final AssociationInfo info = dicomReceiver.getAssociationMap().get(as);
    if (info == null) {
      throw new DicomServiceException(request, Status.ProcessingFailure, "Invalid or unknown association");
    }
    if (!info.canConnect) {
      Audit.log(as.getCallingAET() + "@" + as.getSocket().getInetAddress().getHostName(), "association_rejected", "C-MOVE");
      throw new DicomServiceException(request, Status.ProcessingFailure, "AET (" + as.getCalledAET() + ") is unknown");
    }

    // The calling machine can connect, see if we can find the outbound
    // machine...
    final String destinationAET = command.getString(Tag.MoveDestination);
    final Device destination = new Device();
    template.query("select HostName, Port from Device where ApplicationEntityTitle = ? and PoolKey = ?", new Object[] { destinationAET, info.poolKey }, new RowCallbackHandler() {

      @Override
      public void processRow(ResultSet rs) throws SQLException {
        destination.applicationEntityTitle = destinationAET;
        destination.port = rs.getInt("Port");
        destination.hostName = rs.getString("HostName");
      }
    });
    if (destination.applicationEntityTitle == null) {
      // 0xa801 is refused: Move Destination unknown
      Audit.log(as.getCallingAET() + "@" + as.getSocket().getInetAddress().getHostName(), "unknown_destination", "C-MOVE");
      as.writeDimseRSP(pcid, CommandUtils.mkRSP(command, 0xa801));
      return;
    }

    // Construct an object for the audit log
    final ObjectNode node = objectMapper.createObjectNode();
    node.put("RemoteDevice", destination.toString());
    node.put("CalledAETitle", poolManager.getContainer(info.poolKey).getPool().applicationEntityTitle);
    final String remoteDevice = as.getCallingAET() + "@" + as.getSocket().getInetAddress().getHostName();
    final String retrieveAETitle = (as.getLocalAET() == null) ? as.getCalledAET() : as.getLocalAET();
    node.put("RetrieveAETitle", retrieveAETitle);

    // Find the studies to send
    // Find the series we need to move
    String retrieveLevel = request.getString(Tag.QueryRetrieveLevel);
    List<Integer> seriesKeyList = new ArrayList<Integer>();
    if (retrieveLevel.equalsIgnoreCase("STUDY")) {
      String uid = request.getString(Tag.StudyInstanceUID);
      seriesKeyList.addAll(template.queryForList("select SERIES.SeriesKey from SERIES, STUDY where SERIES.StudyKey = STUDY.StudyKey and STUDY.StudyInstanceUID = ? and STUDY.PoolKey = ?", Integer.class, uid, info.poolKey));
      node.put("StudyInstanceUID", uid);
    }
    if (retrieveLevel.equalsIgnoreCase("SERIES")) {
      String uid = request.getString(Tag.SeriesInstanceUID);
      seriesKeyList.addAll(template.queryForList("select SERIES.SeriesKey from SERIES, STUDY where SERIES.SeriesInstanceUID = ? and STUDY.StudyKey = SERIES.StudyKey and STUDY.PoolKey = ?", Integer.class, uid, info.poolKey));
      node.put("SeriesInstanceUID", uid);
    }
    if (seriesKeyList.size() == 0) {
      // 0xa801 is Unable to calculate number of matches
      as.writeDimseRSP(pcid, CommandUtils.mkRSP(command, 0xa701));
      Audit.log(remoteDevice, "no_matching_series", "C-MOVE");
      return;
    }

    final DcmSnd sender = new DcmSnd(as.getCalledAET());
    sender.setCalledAET(destination.applicationEntityTitle);
    sender.setRemoteHost(destination.hostName);
    sender.setRemotePort(destination.port);
    sender.setCalling(as.getCalledAET());

    for (Integer key : seriesKeyList) {
      template.query("select FilePath from INSTANCE where SeriesKey = ?", new Object[] { key }, new RowCallbackHandler() {

        @Override
        public void processRow(ResultSet rs) throws SQLException {
          File f = new File(info.poolRootDirectory, rs.getString("FilePath"));
          sender.addFile(f);
          imageQueueCounter.inc();
        }
      });
    }
    node.put("NumberOfSeries", seriesKeyList.size());
    node.put("NumberOfInstances", imageQueueCounter.getCount());

    FileMovedHandler callback = new FileMovedHandler() {

      @Override
      public void fileMoved(int current, int total) {
        DicomObject response = CommandUtils.mkRSP(command, Status.Pending);
        response.putInt(Tag.NumberOfCompletedSuboperations, VR.US, current + 1);
        response.putInt(Tag.NumberOfRemainingSuboperations, VR.US, total - current - 1);
        response.putInt(Tag.NumberOfFailedSuboperations, VR.US, 0);
        response.putInt(Tag.NumberOfWarningSuboperations, VR.US, 0);
        imageMeter.mark();
        imageSentCounter.inc();
        imagesPerSecond.mark();
        try {
          if (logger.isDebugEnabled()) {
            logger.debug("Sending " + current + " of " + total + " images");
            logger.debug("Returning Response: \n" + response);
          }
          as.writeDimseRSP(pcid, response);
        } catch (Exception e) {
          logger.error("Failed to write return response", e);
        }
        imageQueueCounter.dec();
      }
    };
    sender.configureTransferCapability();

    try {
      sender.open();
      sender.send(callback);
    } catch (Exception e) {
      logger.error("ERROR: Failed to send", e);
      Audit.log(remoteDevice, "failed_to_send", "C-MOVE: " + e.getMessage());
    } finally {
      sender.close();
    }

    as.writeDimseRSP(pcid, CommandUtils.mkRSP(command, Status.Success));
    Audit.log(remoteDevice, "move_success", node);
  }
}
