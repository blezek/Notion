package edu.mayo.qia.pacs.components;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.hibernate.annotations.Type;

@Entity
@Table(name = "queryresult")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Result {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public int queryResultKey = -1;

  @ManyToOne(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
  @JoinColumn(name = "QueryItemKey")
  @JsonIgnore
  private Item item;

  public String status;
  @Type(type = "true_false")
  public Boolean doFetch;
  public String patientName;
  public String patientID;
  public String accessionNumber;
  public String patientBirthDate;
  public String studyDate;
  public String modalitiesInStudy;
  public String studyDescription;
  public String studyInstanceUID;
  public Integer studyKey;
}
