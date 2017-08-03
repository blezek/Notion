package edu.mayo.qia.pacs.components;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Device {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public int deviceKey = -1;
  public String applicationEntityTitle = null;
  public String hostName = null;
  public String description = null;
  public int port = 0;
  public String callingApplicationEntityTitle = null;
  @Column(columnDefinition = "INTEGER")
  public boolean isAutoforward = false;

  @ManyToOne(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
  @JoinColumn(name = "PoolKey")
  @JsonIgnore
  public Pool pool;

  public Device() {
  }

  public Device(String applicationEntityTitle, String hostName, int port) {
    this(applicationEntityTitle, hostName, port, null, null);
  }

  public Device(String applicationEntityTitle, String hostName, int port, Pool pool) {
    this(applicationEntityTitle, hostName, port, null, pool);
  }

  public Device(String applicationEntityTitle, String hostName, int port, String callingApplicationEntityTitle, Pool pool) {
    this.applicationEntityTitle = applicationEntityTitle;
    this.hostName = hostName;
    this.port = port;
    this.pool = pool;
    this.callingApplicationEntityTitle = callingApplicationEntityTitle;
  }

  @JsonIgnore
  public Pool getPool() {
    return pool;
  }

  public void setPool(Pool pool) {
    this.pool = pool;
  }

  public void update(Device update) {
    this.applicationEntityTitle = update.applicationEntityTitle;
    this.description = update.description;
    this.port = update.port;
    this.hostName = update.hostName;
    this.isAutoforward = update.isAutoforward;
  }

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    b.append(applicationEntityTitle).append("@").append(hostName).append(":").append(port);
    return b.toString();
  }

}
