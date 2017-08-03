package edu.mayo.qia.pacs.components;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;

import java.util.Date;

@Entity
@Table
public class Instance {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public int InstanceKey;

  @ManyToOne(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
  @JoinColumn(name = "SeriesKey")
  public Series series;

  public String SOPInstanceUID;
  public String SOPClassUID;
  public String InstanceNumber;
  public Date ContentDate;
  public Date ContentTime;
  public String FilePath;

  public Instance() {
  }

  public Instance(DicomObject tags, String path) {
    SOPInstanceUID = tags.getString(Tag.SOPInstanceUID);
    this.update(tags);
    FilePath = path;
  }

  public void update(DicomObject tags) {
    SOPClassUID = tags.getString(Tag.SOPClassUID);
    InstanceNumber = tags.getString(Tag.InstanceNumber);
    ContentDate = tags.getDate(Tag.ContentDate);
    ContentTime = tags.getDate(Tag.ContentTime);
  }
}
