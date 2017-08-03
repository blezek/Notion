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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DateRange;
import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.UID;
import org.dcm4che2.data.VR;
import org.dcm4che2.net.Association;
import org.dcm4che2.net.CommandUtils;
import org.dcm4che2.net.DicomServiceException;
import org.dcm4che2.net.Status;
import org.dcm4che2.net.service.CFindSCP;
import org.dcm4che2.net.service.DicomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import edu.mayo.qia.pacs.Audit;
import edu.mayo.qia.pacs.Notion;
import edu.mayo.qia.pacs.components.Pool;
import edu.mayo.qia.pacs.components.PoolManager;
import edu.mayo.qia.pacs.ctp.Anonymizer;
import edu.mayo.qia.pacs.dicom.DICOMReceiver.AssociationInfo;

@Component
public class FindSCP extends DicomService implements CFindSCP {
  static Logger logger = LoggerFactory.getLogger(FindSCP.class);
  public static String[] PresentationContexts = new String[] { UID.StudyRootQueryRetrieveInformationModelFIND, UID.PatientRootQueryRetrieveInformationModelFIND };

  @Autowired
  JdbcTemplate template;

  @Autowired
  PoolManager poolManager;

  @Autowired
  ObjectMapper objectMapper;

  public FindSCP() {
    super(PresentationContexts);
  }

  void addWhere(int tag, String column, DicomObject data, List<Object> args, StringBuilder query) {
    if (data.contains(tag) && data.containsValue(tag)) {
      query.append(" AND " + column + " like ? ");
      args.add(data.getString(tag).replace("*", "%").replace("?", "_"));
    }
  }

  @Override
  public void cfind(final Association as, final int pcid, DicomObject rq, final DicomObject data) throws DicomServiceException, IOException {
    DICOMReceiver dicomReceiver = Notion.context.getBean("dicomReceiver", DICOMReceiver.class);
    AssociationInfo info = dicomReceiver.getAssociationMap().get(as);

    final String remoteDevice = as.getCallingAET() + "@" + as.getSocket().getInetAddress().getHostName();

    if (info == null) {
      throw new DicomServiceException(rq, Status.ProcessingFailure, "Invalid or unknown association");
    }
    if (poolManager.getContainer(info.poolKey) == null) {
      Audit.log(remoteDevice, "association_rejected", "C-FIND: unknown image pool");
      throw new DicomServiceException(rq, Status.ProcessingFailure, "Unknown image pool");
    }
    Pool pool = poolManager.getContainer(info.poolKey).getPool();

    if (!info.canConnect) {
      Audit.log(remoteDevice, "association_rejected", "C-FIND to " + pool);
      throw new DicomServiceException(rq, Status.ProcessingFailure, info.failureMessage);
    }

    logger.debug("Got request: \n" + rq);
    logger.debug("Got data: \n" + data);

    // Find the retrieve level
    if (!data.contains(Tag.QueryRetrieveLevel)) {
      // Write an error
      as.writeDimseRSP(pcid, CommandUtils.mkRSP(rq, Status.ProcessingFailure));
      return;
    }
    final String retrieveLevel = data.get(Tag.QueryRetrieveLevel).getString(null, true);
    logger.debug("Retrieve level: " + retrieveLevel);

    // We should send back the RetrieveAET
    final String retrieveAETitle = (as.getLocalAET() == null) ? as.getCalledAET() : as.getLocalAET();

    final DicomObject pending = CommandUtils.mkRSP(rq, Status.Pending);

    if (retrieveLevel.equalsIgnoreCase("SERIES")) {
      if (data.containsValue(Tag.StudyInstanceUID)) {
        final Map<Integer, String> tagColumn = new HashMap<Integer, String>();
        tagColumn.put(Tag.Modality, "Modality");
        tagColumn.put(Tag.InstitutionName, "InstitutionName");
        tagColumn.put(Tag.SeriesDescription, "SeriesDescription");
        tagColumn.put(Tag.SeriesInstanceUID, "SeriesInstanceUID");
        tagColumn.put(Tag.SeriesNumber, "SeriesNumber");
        tagColumn.put(Tag.NumberOfSeriesRelatedInstances, "NumberOfSeriesRelatedInstances");

        final String uid = data.getString(Tag.StudyInstanceUID);
        StringBuilder query = new StringBuilder();
        query.append("select ");
        for (String column : tagColumn.values()) {
          query.append("SERIES." + column + " AS " + column + ", ");
        }

        // Don't want these in the query string
        tagColumn.put(Tag.SeriesDate, "SeriesDate");
        tagColumn.put(Tag.SeriesTime, "SeriesTime");

        query.append(" STUDY.StudyDate as SeriesDate, STUDY.StudyTime as SeriesTime, from SERIES, STUDY where STUDY.StudyInstanceUID = ? and STUDY.StudyKey = SERIES.StudyKey");
        logger.debug("SERIES Query: " + query);
        logger.debug("StudyUID: " + uid);
        template.query(query.toString(), new Object[] { uid }, new RowCallbackHandler() {

          @Override
          public void processRow(ResultSet rs) throws SQLException {
            logger.debug("Found SERIES: " + rs.getString("SeriesInstanceUID"));

            ObjectNode node = objectMapper.createObjectNode();
            node.put("RemoteDevice", remoteDevice);
            node.put("RetrieveAETitle", retrieveAETitle);
            DicomObject response = new BasicDicomObject();

            // Always send the Query/Retrieve level C.4.1.1.3.2
            response.putString(Tag.QueryRetrieveLevel, VR.CS, retrieveLevel);
            node.put(Anonymizer.fieldMap.get(Tag.QueryRetrieveLevel), retrieveLevel);

            // RetrieveAETitle is also required C.4.1.1.3.2
            response.putString(Tag.RetrieveAETitle, VR.AE, retrieveAETitle);
            node.put(Anonymizer.fieldMap.get(Tag.RetrieveAETitle), retrieveAETitle);

            // Just return what was asked for, if we have it
            Iterator<DicomElement> iterator = data.datasetIterator();
            while (iterator.hasNext()) {
              DicomElement element = iterator.next();
              if (tagColumn.containsKey(element.tag())) {
                String column = tagColumn.get(element.tag());
                int columnNumber = rs.findColumn(column);
                // Figure out what type it is (string or data)
                int columnType = rs.getMetaData().getColumnType(columnNumber);
                if (columnType == Types.VARCHAR || columnType == Types.INTEGER) {
                  response.putString(element.tag(), element.vr(), rs.getString(columnNumber));
                  node.put(Anonymizer.fieldMap.get(element.tag()), rs.getString(columnNumber));
                }
                if (columnType == Types.DATE) {
                  response.putDate(element.tag(), element.vr(), rs.getDate(columnNumber));
                  node.put(Anonymizer.fieldMap.get(element.tag()), rs.getDate(columnNumber).toString());
                } else if (columnType == Types.TIME) {
                  response.putDate(element.tag(), element.vr(), rs.getTime(columnNumber));
                  node.put(Anonymizer.fieldMap.get(element.tag()), rs.getTime(columnNumber).toString());
                }
              } else {
                logger.error("No match for " + element);
                if (!data.containsValue(element.tag())) {
                  response.putString(element.tag(), element.vr(), "  ");
                }
              }
            }
            response.putString(Tag.StudyInstanceUID, VR.UI, uid);
            if (data.containsValue(Tag.SpecificCharacterSet)) {
              response.putString(Tag.SpecificCharacterSet, VR.CS, data.getString(Tag.SpecificCharacterSet));
            }

            Audit.log(remoteDevice, "find_success", node);
            try {
              logger.debug("Sending \n" + response);
              as.writeDimseRSP(pcid, pending, response);
            } catch (IOException e) {
              logger.error("Error writing response", e);
            }
          }
        });
      }
    }

    if (retrieveLevel.equalsIgnoreCase("PATIENT") || retrieveLevel.equalsIgnoreCase("STUDY")) {
      final Map<Integer, String> tagColumn = new HashMap<Integer, String>();
      // Patient Queries
      tagColumn.put(Tag.PatientName, "Patientname");
      tagColumn.put(Tag.PatientID, "PatientID");
      tagColumn.put(Tag.PatientBirthDate, "PatientBirthDate");
      tagColumn.put(Tag.PatientSex, "PatientSex");
      // Study Queries
      tagColumn.put(Tag.StudyDate, "StudyDate");
      tagColumn.put(Tag.StudyTime, "StudyTime");
      tagColumn.put(Tag.StudyID, "StudyID");
      tagColumn.put(Tag.AccessionNumber, "AccessionNumber");
      tagColumn.put(Tag.StudyInstanceUID, "StudyInstanceUID");
      tagColumn.put(Tag.StudyDescription, "StudyDescription"); // Do a patient
                                                               // query
      tagColumn.put(Tag.NumberOfStudyRelatedInstances, "NumberOfStudyRelatedInstances");
      tagColumn.put(Tag.NumberOfStudyRelatedSeries, "NumberOfStudyRelatedSeries");

      ArrayList<Object> args = new ArrayList<Object>();
      StringBuilder query = new StringBuilder();
      query.append("select");
      StringBuilder tables = new StringBuilder();
      tables.append(" from STUDY"); // Space at beginning to prevent running
                                    // into existing text

      // query.append("select PatientID, Patientname, PatientBirthDate, PatientSex, StudyID, StudyDate, StudyTime, AccessionNumber, StudyInstanceUID, StudyDescription, StudyKey from STUDY "
      // WHERE 1=1 ");
      query.append(" PatientID, Patientname, PatientBirthDate, PatientSex");
      query.append(", StudyID, StudyDate, StudyTime, AccessionNumber, StudyInstanceUID, StudyDescription, STUDY.StudyKey as StudyKey");
      if (data.contains(Tag.NumberOfStudyRelatedSeries) || data.contains(Tag.NumberOfStudyRelatedInstances)) {
        query.append(", count(distinct(SERIES.SeriesKey)) as NumberOfStudyRelatedSeries");
        tables.append(" join SERIES on SERIES.StudyKey = STUDY.StudyKey");
      }
      if (data.contains(Tag.NumberOfStudyRelatedInstances)) {
        query.append(", count(distinct(SOPInstanceUID)) as NumberOfStudyRelatedInstances");
        tables.append(" join INSTANCE on INSTANCE.SeriesKey = SERIES.SeriesKey");
      }
      // Add on the list of tables to query from
      query.append(tables);
      query.append(" where STUDY.PoolKey = ? ");
      args.add(info.poolKey);
      addWhere(Tag.PatientName, tagColumn.get(Tag.PatientName), data, args, query);
      addWhere(Tag.PatientID, tagColumn.get(Tag.PatientID), data, args, query);
      addWhere(Tag.AccessionNumber, tagColumn.get(Tag.AccessionNumber), data, args, query);
      addWhere(Tag.StudyID, tagColumn.get(Tag.StudyID), data, args, query);
      if (data.contains(Tag.StudyDate) && data.containsValue(Tag.StudyDate)) {
        DateRange range = data.getDateRange(Tag.StudyDate);
        if (range.getStart() != null) {
          query.append("AND StudyDate >= ? ");
          args.add(range.getStart());
        }
        if (range.getEnd() != null) {
          query.append(" and StudyDate <= ? ");
          args.add(range.getEnd());
        }
      }
      // Finally, add a group by clause to enable the image/series counting
      // NB: for Derby, all the non-aggregate columns must be listed
      query.append(" group by STUDY.StudyKey, PatientID,  Patientname,  PatientBirthDate,  PatientSex,  StudyID,  StudyDate,  StudyTime,  AccessionNumber,  StudyInstanceUID,  StudyDescription");

      // Need to handle Dates and times
      try {
        logger.debug("Ready to run qeury " + query);
        logger.debug("Arguments: " + args);

        template.query(query.toString(), args.toArray(), new RowCallbackHandler() {

          @Override
          public void processRow(ResultSet rs) throws SQLException {
            DicomObject response = new BasicDicomObject();
            final ObjectNode node = objectMapper.createObjectNode();
            node.put("RemoteDevice", remoteDevice);
            node.put("RetrieveAETitle", retrieveAETitle);

            // Just return what was asked for, if we have it
            Iterator<DicomElement> iterator = data.datasetIterator();
            while (iterator.hasNext()) {
              DicomElement element = iterator.next();
              if (tagColumn.containsKey(element.tag())) {
                String column = tagColumn.get(element.tag());
                int columnNumber = rs.findColumn(column);
                logger.debug(element.toString() + " maps to Column " + columnNumber + " " + column + " with type " + rs.getMetaData().getColumnTypeName(columnNumber));

                // Figure out what type it is (string or data)
                int colType = rs.getMetaData().getColumnType(columnNumber);
                if (colType == Types.VARCHAR || colType == Types.INTEGER || colType == Types.BIGINT) {
                  response.putString(element.tag(), element.vr(), rs.getString(columnNumber));
                } else if (colType == Types.DATE) {
                  java.sql.Date studyDateValue = rs.getDate(columnNumber);
                  response.putDate(element.tag(), element.vr(), studyDateValue);
                } else if (colType == Types.TIME) {
                  java.sql.Time studyTimeValue = rs.getTime(columnNumber);
                  response.putDate(element.tag(), element.vr(), studyTimeValue);
                }
              }
            }
            // Do we have Modalities?
            if (data.contains(Tag.ModalitiesInStudy)) {
              List<String> modalityList = template.queryForList("select distinct ( Modality ) from SERIES where StudyKey = ?", new Object[] { rs.getInt("StudyKey") }, String.class);
              response.putStrings(Tag.ModalitiesInStudy, VR.CS, modalityList.toArray(new String[] {}));
              ArrayNode a = node.withArray(Anonymizer.fieldMap.get(Tag.ModalitiesInStudy));
              for (String v : modalityList) {
                a.add(v);
              }
            }
            if (data.containsValue(Tag.StudyInstanceUID)) {
              response.putString(Tag.StudyInstanceUID, VR.UI, data.getString(Tag.StudyInstanceUID));
              node.put(Anonymizer.fieldMap.get(Tag.StudyInstanceUID), data.getString(Tag.StudyInstanceUID));
            }
            // Always send the Query/Retrieve level C.4.1.1.3.2
            response.putString(Tag.QueryRetrieveLevel, VR.CS, retrieveLevel);
            node.put(Anonymizer.fieldMap.get(Tag.QueryRetrieveLevel), retrieveLevel);

            // RetrieveAETitle is also required C.4.1.1.3.2
            response.putString(Tag.RetrieveAETitle, VR.AE, retrieveAETitle);
            node.put(Anonymizer.fieldMap.get(Tag.RetrieveAETitle), retrieveAETitle);
            Audit.log(remoteDevice, "find_success", node);
            try {
              logger.debug("Sending \n" + response);
              as.writeDimseRSP(pcid, pending, response);
            } catch (IOException e) {
              logger.error("Error writing response", e);
            }
          }
        });

      } catch (Exception e) {
        logger.error("Error finding patients", e);
        as.writeDimseRSP(pcid, CommandUtils.mkRSP(rq, Status.ProcessingFailure));
        return;
      }
    }
    // All done
    as.writeDimseRSP(pcid, CommandUtils.mkRSP(rq, CommandUtils.SUCCESS), null);
  }
}
