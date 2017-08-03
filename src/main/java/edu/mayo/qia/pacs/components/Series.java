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

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table
public final class Series {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public int SeriesKey;

  @ManyToOne(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
  @JoinColumn(name = "StudyKey")
  public Study study;

  @OneToMany(cascade = CascadeType.ALL, mappedBy = "series")
  public Set<Instance> instances = new HashSet<Instance>();

  public String SeriesInstanceUID;
  public String SeriesNumber;
  public String Modality;
  public String BodyPartExamined;
  public String Laterality;
  public String SeriesDescription;
  public String InstitutionName;
  public String StationName;
  public String InstitutionalDepartmentName;
  public String PerformingPhysicianName;
  public int NumberOfSeriesRelatedInstances;

  public Series() {
  }

  public Series(DicomObject tags) {
    SeriesInstanceUID = tags.getString(Tag.SeriesInstanceUID);
    this.update(tags);
  }

  public void update(DicomObject tags) {
    SeriesNumber = tags.getString(Tag.SeriesNumber);
    Modality = tags.getString(Tag.Modality);
    BodyPartExamined = tags.getString(Tag.BodyPartExamined);
    Laterality = tags.getString(Tag.Laterality);
    SeriesDescription = tags.getString(Tag.SeriesDescription);
    InstitutionName = tags.getString(Tag.InstitutionName);
    StationName = tags.getString(Tag.StationName);
    InstitutionalDepartmentName = tags.getString(Tag.InstitutionalDepartmentName);
    PerformingPhysicianName = tags.getString(Tag.PerformingPhysicianName);
    NumberOfSeriesRelatedInstances = tags.getInt(Tag.NumberOfSeriesRelatedInstances, 0);
  }

}
