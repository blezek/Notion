package edu.mayo.qia.pacs.test;

import org.dcm4che2.data.UID;
import org.dcm4che2.net.Association;
import org.dcm4che2.net.ConfigurationException;
import org.dcm4che2.net.Device;
import org.dcm4che2.net.NetworkApplicationEntity;
import org.dcm4che2.net.NetworkConnection;
import org.dcm4che2.net.NewThreadExecutor;
import org.dcm4che2.net.TransferCapability;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;

@RunWith(SpringJUnit4ClassRunner.class)
public class DICOMTest extends PACSTest {

  @Test
  public void CEcho() throws ConfigurationException, IOException, InterruptedException {
    NetworkApplicationEntity remoteAE = new NetworkApplicationEntity();
    NetworkConnection remoteConn = new NetworkConnection();
    Device device;
    NetworkApplicationEntity ae = new NetworkApplicationEntity();
    NetworkConnection conn = new NetworkConnection();
    NewThreadExecutor executor = new NewThreadExecutor("test");

    device = new Device("RPACS");

    remoteAE.setInstalled(true);
    remoteAE.setAssociationAcceptor(true);
    remoteAE.setNetworkConnection(new NetworkConnection[] { remoteConn });

    device.setNetworkApplicationEntity(ae);
    device.setNetworkConnection(conn);

    ae.setNetworkConnection(conn);
    ae.setAssociationInitiator(true);
    ae.setAETitle("RPACS-TEST");
    String[] DEF_TS = { UID.ImplicitVRLittleEndian };
    TransferCapability VERIFICATION_SCU = new TransferCapability(UID.VerificationSOPClass, DEF_TS, TransferCapability.SCU);

    ae.setTransferCapability(new TransferCapability[] { VERIFICATION_SCU });
    remoteConn.setHostname("localhost");
    remoteConn.setPort(DICOMPort);
    remoteAE.setAETitle("RPACS");
    remoteAE.setTransferCapability(new TransferCapability[] { VERIFICATION_SCU });
    Association assoc = ae.connect(remoteAE, executor);
    assoc.cecho().next();
    assoc.release(true);
  }

}
