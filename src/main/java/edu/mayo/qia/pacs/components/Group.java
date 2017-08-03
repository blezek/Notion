package edu.mayo.qia.pacs.components;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "GROUPS")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Group {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public int groupKey;

  public String name;
  public String description;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "USERGROUP", joinColumns = @JoinColumn(name = "GroupKey"))
  @Column(name = "UserKey")
  public Set<Integer> userKeys = new HashSet<Integer>();

}
