package edu.mayo.qia.pacs.components;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "queryitem")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Item {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public int queryItemKey = -1;

  @ManyToOne(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
  @JoinColumn(name = "QueryKey")
  @JsonIgnore
  public Query query;

  public String status;
  public String patientName;
  public String patientID;
  public String accessionNumber;
  public String patientBirthDate;
  public String studyDate;
  public String modalitiesInStudy = "";
  public String studyDescription;
  public String anonymizedName;
  public String anonymizedID;

  @OneToMany(cascade = CascadeType.ALL, mappedBy = "item", fetch = FetchType.EAGER)
  public Set<Result> items = new HashSet<Result>();

  @JsonIgnore
  public Map<String, String> getTagMap() {
    Map<String, String> map = new HashMap<String, String>();
    // @formatter:off
    if ( patientName != null ) { map.put("PatientName", patientName); }
    if ( patientName != null ) { map.put ("PatientName", patientName ); }
    if ( patientID != null ) { map.put ("PatientID", patientID ); }
    if ( accessionNumber != null ) { map.put ("AccessionNumber", accessionNumber ); }
    if ( patientBirthDate != null ) { map.put ("PatientBirthDate", patientBirthDate ); }
    if ( studyDate != null ) { map.put ("StudyDate", studyDate ); }
    if ( modalitiesInStudy != null ) { map.put ("ModalitiesInStudy", modalitiesInStudy ); }
    if ( studyDescription != null ) { map.put ("StudyDescription", studyDescription ); }
    // @formatter:on
    return map;
  }

}
