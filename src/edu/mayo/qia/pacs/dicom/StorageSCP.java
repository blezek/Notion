package edu.mayo.qia.pacs.dicom;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.UID;
import org.dcm4che2.io.DicomOutputStream;
import org.dcm4che2.net.Association;
import org.dcm4che2.net.DicomServiceException;
import org.dcm4che2.net.PDVInputStream;
import org.dcm4che2.net.Status;
import org.dcm4che2.net.service.StorageService;
import org.dcm4che2.util.CloseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import edu.mayo.qia.pacs.components.PoolManager;
import edu.mayo.qia.pacs.dicom.DICOMReceiver.AssociationInfo;
import edu.mayo.qia.pacs.message.ProcessIncomingInstance;

@Component
public class StorageSCP extends StorageService {
  static Logger logger = LoggerFactory.getLogger(StorageSCP.class);

  @Autowired
  JdbcTemplate template;

  @Autowired
  JmsTemplate jmsTemplate;

  @Autowired
  PoolManager poolManager;

  @Autowired
  TaskExecutor taskExecutor;

  @Autowired
  DICOMReceiver dicomReceiver;

  public static final String[] CUIDS = { UID.BasicStudyContentNotificationSOPClassRetired, UID.StoredPrintStorageSOPClassRetired, UID.HardcopyGrayscaleImageStorageSOPClassRetired, UID.HardcopyColorImageStorageSOPClassRetired,
      UID.ComputedRadiographyImageStorage, UID.DigitalXRayImageStorageForPresentation, UID.DigitalXRayImageStorageForProcessing, UID.DigitalMammographyXRayImageStorageForPresentation, UID.DigitalMammographyXRayImageStorageForProcessing,
      UID.DigitalIntraoralXRayImageStorageForPresentation, UID.DigitalIntraoralXRayImageStorageForProcessing, UID.StandaloneModalityLUTStorageRetired, UID.EncapsulatedPDFStorage, UID.StandaloneVOILUTStorageRetired,
      UID.GrayscaleSoftcopyPresentationStateStorageSOPClass, UID.ColorSoftcopyPresentationStateStorageSOPClass, UID.PseudoColorSoftcopyPresentationStateStorageSOPClass, UID.BlendingSoftcopyPresentationStateStorageSOPClass,
      UID.XRayAngiographicImageStorage, UID.EnhancedXAImageStorage, UID.XRayRadiofluoroscopicImageStorage, UID.EnhancedXRFImageStorage, UID.XRayAngiographicBiPlaneImageStorageRetired, UID.PositronEmissionTomographyImageStorage,
      UID.StandalonePETCurveStorageRetired, UID.CTImageStorage, UID.EnhancedCTImageStorage, UID.NuclearMedicineImageStorage, UID.UltrasoundMultiframeImageStorageRetired, UID.UltrasoundMultiframeImageStorage, UID.MRImageStorage, UID.EnhancedMRImageStorage,
      UID.MRSpectroscopyStorage, UID.RTImageStorage, UID.RTDoseStorage, UID.RTStructureSetStorage, UID.RTBeamsTreatmentRecordStorage, UID.RTPlanStorage, UID.RTBrachyTreatmentRecordStorage, UID.RTTreatmentSummaryRecordStorage,
      UID.NuclearMedicineImageStorageRetired, UID.UltrasoundImageStorageRetired, UID.UltrasoundImageStorage, UID.RawDataStorage, UID.SpatialRegistrationStorage, UID.SpatialFiducialsStorage, UID.RealWorldValueMappingStorage,
      UID.SecondaryCaptureImageStorage, UID.MultiframeSingleBitSecondaryCaptureImageStorage, UID.MultiframeGrayscaleByteSecondaryCaptureImageStorage, UID.MultiframeGrayscaleWordSecondaryCaptureImageStorage,
      UID.MultiframeTrueColorSecondaryCaptureImageStorage, UID.VLImageStorageTrialRetired, UID.VLEndoscopicImageStorage, UID.VideoEndoscopicImageStorage, UID.VLMicroscopicImageStorage, UID.VideoMicroscopicImageStorage,
      UID.VLSlideCoordinatesMicroscopicImageStorage, UID.VLPhotographicImageStorage, UID.VideoPhotographicImageStorage, UID.OphthalmicPhotography8BitImageStorage, UID.OphthalmicPhotography16BitImageStorage, UID.StereometricRelationshipStorage,
      UID.VLMultiframeImageStorageTrialRetired, UID.StandaloneOverlayStorageRetired, UID.BasicTextSRStorage, UID.EnhancedSRStorage, UID.ComprehensiveSRStorage, UID.ProcedureLogStorage, UID.MammographyCADSRStorage, UID.KeyObjectSelectionDocumentStorage,
      UID.ChestCADSRStorage, UID.XRayRadiationDoseSRStorage, UID.EncapsulatedPDFStorage, UID.EncapsulatedCDAStorage, UID.StandaloneCurveStorageRetired, UID._12leadECGWaveformStorage, UID.GeneralECGWaveformStorage, UID.AmbulatoryECGWaveformStorage,
      UID.HemodynamicWaveformStorage, UID.CardiacElectrophysiologyWaveformStorage, UID.BasicVoiceAudioWaveformStorage, UID.HangingProtocolStorage, UID.SiemensCSANonImageStorage, UID.Dcm4cheAttributesModificationNotificationSOPClass };

  public StorageSCP() {
    super(CUIDS);
  }

  @Override
  protected void onCStoreRQ(final Association as, int pcid, DicomObject rq, PDVInputStream dataStream, String tsuid, DicomObject rsp) throws DicomServiceException {
    logger.info("Got request");

    AssociationInfo info = dicomReceiver.getAssociationMap().get(as);
    if (info == null) {
      throw new DicomServiceException(rq, Status.ProcessingFailure, "Invalid or unknown association");

    }
    if (!info.canConnect) {
      throw new DicomServiceException(rq, Status.ProcessingFailure, "AET (" + as.getCalledAET() + ") is unknown");
    }

    String cuid = rq.getString(Tag.AffectedSOPClassUID);
    String iuid = rq.getString(Tag.AffectedSOPInstanceUID);

    File root = info.root;
    // Here we want to use a UUID so we don't have duplicates...
    UUID uuid = UUID.randomUUID();

    File file = new File(root, uuid.toString() + ".part");
    try {
      int fileBufferSize = 1024;

      DicomOutputStream dos = new DicomOutputStream(new BufferedOutputStream(new FileOutputStream(file), fileBufferSize));
      try {
        BasicDicomObject fmi = new BasicDicomObject();
        fmi.initFileMetaInformation(cuid, iuid, tsuid);
        dos.writeFileMetaInformation(fmi);
        dataStream.copyTo(dos);
      } finally {
        CloseUtils.safeClose(dos);
      }
    } catch (IOException e) {
      throw new DicomServiceException(rq, Status.ProcessingFailure, e.getMessage());
    }

    // Rename the file after it has been written. See DCM-279
    // XB3 File rename = new File(file.getParent(), iuid);
    final File rename = new File(root, uuid.toString());
    file.renameTo(rename);
    logger.info("Saving file to " + rename);

    boolean singleThreaded = true;
    if (singleThreaded) {
      try {
        poolManager.handleMessage(new ProcessIncomingInstance(as, rename));
      } catch (Exception e) {
        logger.error("Error handling new instance", e);
      }

    } else {

      taskExecutor.execute(new Runnable() {

        @Override
        public void run() {
          try {
            poolManager.handleMessage(new ProcessIncomingInstance(as, rename));
          } catch (Exception e) {
            logger.error("Error handling new instance", e);
          }
        }
      });
      // jmsTemplate.convertAndSend(PACS.sorterQueue, );
      logger.info("Done");
    }
  }

}
