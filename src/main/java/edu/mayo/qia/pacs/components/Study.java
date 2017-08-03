package edu.mayo.qia.pacs.components;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table
public class Study {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public int StudyKey;

  @ManyToOne(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
  @JoinColumn(name = "PoolKey")
  public Pool pool;

  @JsonIgnore
  @OneToMany(cascade = CascadeType.ALL, mappedBy = "study")
  // @JoinColumn(name = "PoolKey")
  public Set<Series> series = new HashSet<Series>();

  public String StudyInstanceUID;
  public String AccessionNumber = "";
  public String PatientID = "";
  public String PatientName = "";
  public Date PatientBirthDate;
  public String PatientSex = "";
  public String StudyID = "";
  public Date StudyDate;
  public Date StudyTime;

  public String ReferringPhysicianName;
  public String StudyDescription;

  public Study() {
  }

  public Study(DicomObject tags) {
    StudyInstanceUID = tags.getString(Tag.StudyInstanceUID);
    this.update(tags);
  }

  public void update(DicomObject tags) {
    AccessionNumber = tags.getString(Tag.AccessionNumber);
    PatientID = tags.getString(Tag.PatientID);
    PatientName = tags.getString(Tag.PatientName);
    PatientBirthDate = tags.getDate(Tag.PatientBirthDate);
    PatientSex = tags.getString(Tag.PatientSex);
    StudyID = tags.getString(Tag.StudyID);
    StudyDate = tags.getDate(Tag.StudyDate);
    StudyTime = tags.getDate(Tag.StudyTime);
    ReferringPhysicianName = tags.getString(Tag.ReferringPhysicianName);
    StudyDescription = tags.getString(Tag.StudyDescription);
  }

  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();
    buffer.append("StudyInstanceUID=" + StudyInstanceUID);
    buffer.append(" AccessionNumber=" + AccessionNumber);
    buffer.append(" PatientID=" + PatientID);
    buffer.append(" PatientName=" + PatientName);
    buffer.append(" PatientBirthDate=" + PatientBirthDate);
    buffer.append(" PatientSex=" + PatientSex);
    buffer.append(" StudyID=" + StudyID);
    buffer.append(" StudyDate=" + StudyDate);
    buffer.append(" StudyTime=" + StudyTime);

    buffer.append(" ReferringPhysicianName=" + ReferringPhysicianName);
    buffer.append(" StudyDescription=" + StudyDescription);
    return buffer.toString();
  }

  public ObjectNode toJson() {
    ObjectNode node = new ObjectMapper().createObjectNode();
    node.put("StudyInstanceUID", StudyInstanceUID);
    node.put("AccessionNumber", AccessionNumber);
    node.put("PatientID", PatientID);
    node.put("PatientName", PatientName);
    node.put("PatientBirthDate", PatientBirthDate == null ? "" : PatientBirthDate.toString());
    node.put("PatientSex", PatientSex);
    node.put("StudyID", StudyID);
    node.put("StudyDate", StudyDate == null ? "" : StudyDate.toString());
    node.put("StudyTime", StudyTime == null ? "" : StudyTime.toString());
    node.put("ReferringPhysicianName", ReferringPhysicianName == null ? "" : ReferringPhysicianName.toString());
    node.put("StudyDescription", StudyDescription == null ? "" : StudyDescription.toString());
    return node;
  }
}
