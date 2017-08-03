package edu.mayo.qia.pacs.dicom;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;

import org.dcm4che2.data.UID;
import org.dcm4che2.net.Association;
import org.dcm4che2.net.AssociationAcceptEvent;
import org.dcm4che2.net.AssociationCloseEvent;
import org.dcm4che2.net.AssociationListener;
import org.dcm4che2.net.Device;
import org.dcm4che2.net.NetworkApplicationEntity;
import org.dcm4che2.net.NetworkConnection;
import org.dcm4che2.net.TransferCapability;
import org.dcm4che2.net.service.VerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

import edu.mayo.qia.pacs.Audit;
import edu.mayo.qia.pacs.Notion;
import edu.mayo.qia.pacs.NotionConfiguration;
import edu.mayo.qia.pacs.components.PoolContainer;
import edu.mayo.qia.pacs.components.PoolManager;
import edu.mayo.qia.pacs.components.ProcessCache;
import io.dropwizard.lifecycle.Managed;

/**
 * Provides a stand-alone DICOM receiver rather than the heavyweight
 * {@link DcmRcv}.
 * 
 * @author Daniel Blezek
 * 
 */
@Component("dicomReceiver")
@DependsOn("flyway")
public class DICOMReceiver implements AssociationListener, Managed {
  static Logger logger = LoggerFactory.getLogger(DICOMReceiver.class);
  private ConcurrentHashMap<Association, AssociationInfo> associationMap = new ConcurrentHashMap<Association, AssociationInfo>();
  static Counter associationCounter = Notion.metrics.counter(MetricRegistry.name("DICOM", "associations", "total"));

  private final Device device = new Device(null);
  private final NetworkApplicationEntity ae = new NetworkApplicationEntity();
  private final NetworkConnection nc = new NetworkConnection();

  @Autowired
  StorageSCP storageSCP;

  @Autowired
  JdbcTemplate template;

  @Autowired
  PoolManager poolManager;

  @Autowired
  NotionConfiguration configuration;

  @Autowired
  FindSCP findSCP;

  @Autowired
  MoveSCP moveSCP;

  // DJB, replaced with Notion's
  // private final Executor executor = Executors.newCachedThreadPool();
  private Executor executor = Notion.executor;

  private static final String[] ONLY_DEF_TS = { UID.ImplicitVRLittleEndian };

  /** Standard constructor */
  public DICOMReceiver() {
    // Register our gauges
    Notion.metrics.register(MetricRegistry.name("DICOM", "associations", "active"), new Gauge<Integer>() {
      @Override
      public Integer getValue() {
        return associationMap.size();
      }
    });
  }

  @Override
  public synchronized void stop() {
    device.stopListening();
  }

  /**
   * Starts the receiver listening.
   * 
   * @throws Exception
   *         if could not start database
   */
  @Override
  public synchronized void start() throws Exception {
    logger.debug("Starting");
    nc.setPort(configuration.notion.dicomPort);
    device.setNetworkApplicationEntity(ae);
    device.setNetworkConnection(nc);
    ae.setNetworkConnection(nc);
    ae.setAssociationAcceptor(true);
    ae.setPackPDV(true);
    nc.setTcpNoDelay(true);

    String[] NON_RETIRED_LE_TS = { UID.JPEGLSLossless, UID.JPEGLossless, UID.JPEGLosslessNonHierarchical14, UID.JPEG2000LosslessOnly, UID.DeflatedExplicitVRLittleEndian, UID.RLELossless, UID.ExplicitVRLittleEndian, UID.ImplicitVRLittleEndian,
        UID.JPEGBaseline1, UID.JPEGExtended24, UID.JPEGLSLossyNearLossless, UID.JPEG2000, UID.MPEG2, };

    ArrayList<TransferCapability> tc = new ArrayList<TransferCapability>();
    tc.add(new TransferCapability(UID.VerificationSOPClass, ONLY_DEF_TS, TransferCapability.SCP));
    for (int i = 0; i < StorageSCP.CUIDS.length; i++) {
      tc.add(new TransferCapability(StorageSCP.CUIDS[i], NON_RETIRED_LE_TS, TransferCapability.SCP));
    }

    for (int i = 0; i < FindSCP.PresentationContexts.length; i++) {
      tc.add(new TransferCapability(FindSCP.PresentationContexts[i], NON_RETIRED_LE_TS, TransferCapability.SCP));
    }
    for (int i = 0; i < MoveSCP.PresentationContexts.length; i++) {
      tc.add(new TransferCapability(MoveSCP.PresentationContexts[i], NON_RETIRED_LE_TS, TransferCapability.SCP));
    }

    if (logger.isDebugEnabled()) {
      for (TransferCapability tt : tc) {
        logger.debug("Added: " + tt.getSopClass());
      }
    }
    ae.setTransferCapability(tc.toArray(new TransferCapability[] {}));
    ae.register(new VerificationService());
    ae.register(storageSCP);
    ae.register(findSCP);
    ae.register(moveSCP);

    ae.addAssociationListener(this);

    device.startListening(executor);
  }

  @Override
  public void associationAccepted(AssociationAcceptEvent event) {
    // Check to see if this AE can connect
    final AssociationInfo info = new AssociationInfo();
    final Association association = event.getAssociation();
    associationMap.put(association, info);
    File incoming = new File(configuration.notion.imageDirectory, "incoming");
    info.incomingRootDirectory = new File(incoming, event.getAssociation().getCalledAET());
    info.incomingRootDirectory.mkdirs();

    final String remoteHostName = association.getSocket().getInetAddress().getHostName();
    final String callingAET = association.getCallingAET();

    if (poolManager.getContainer(association.getCalledAET()) == null) {
      info.canConnect = false;
      info.failureMessage = "Called AETitle [" + association.getCalledAET() + "] is unknown";
      Audit.log(callingAET + "@" + remoteHostName, "association_rejected", "unknown CalledAET " + association.getCalledAET());

      return;
    }
    info.failureMessage = "Calling AETitle [" + association.getCallingAET() + "] is unknown";
    template.query("select Device.ApplicationEntityTitle AS AET,  Device.HostName AS HN, Pool.PoolKey as PK from Device, Pool where Device.PoolKey = Pool.PoolKey and Pool.ApplicationEntityTitle = ?", new Object[] { event.getAssociation().getCalledAET() },
        new RowCallbackHandler() {

          @Override
          public void processRow(ResultSet rs) throws SQLException {
            String AET = rs.getString("AET");
            String HN = rs.getString("HN");
            info.poolKey = rs.getInt("PK");
            // Hostnames _must_ be case insensitive, but AETitles are case
            // sensitive
            boolean matchHostName = Pattern.compile(HN, Pattern.CASE_INSENSITIVE).matcher(remoteHostName).matches();
            boolean matchAETitle = Pattern.compile(AET).matcher(callingAET).matches();
            if (matchHostName && matchAETitle) {
              info.canConnect = true;
            }
          }
        });
    if (info.canConnect && poolManager.getContainer(association.getCalledAET()) != null) {
      info.poolRootDirectory = poolManager.getContainer(association.getCalledAET()).getPoolDirectory();
      associationCounter.inc();
    } else {
      Audit.log(callingAET + "@" + remoteHostName, "association_rejected", "device unknown to pool " + poolManager.getContainer(association.getCalledAET()).getPool().name);
    }
  }

  @Override
  public void associationClosed(AssociationCloseEvent event) {
    AssociationInfo info = associationMap.get(event.getAssociation());
    PoolContainer container = poolManager.getContainer(info.poolKey);
    if (container != null) {
      container.processAnonymizationMap();
    }
    associationMap.remove(event.getAssociation());
  }

  public static class AssociationInfo {
    public String uuid = UUID.randomUUID().toString();
    public boolean canConnect = false;
    public String failureMessage = "Failed to connect";
    public ProcessCache cache = new ProcessCache();
    File incomingRootDirectory;
    File poolRootDirectory;
    int poolKey;
    int imageCount = 0;
  }

  public Map<Association, AssociationInfo> getAssociationMap() {
    return associationMap;
  }

}
