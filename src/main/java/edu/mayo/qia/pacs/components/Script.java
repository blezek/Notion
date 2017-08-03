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

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.springframework.core.io.ClassPathResource;

@Entity
@Table
@JsonIgnoreProperties(ignoreUnknown = true)
public class Script {
  static Logger logger = Logger.getLogger(Script.class);

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public int scriptKey = -1;
  public String script;

  @ManyToOne(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
  @JoinColumn(name = "PoolKey")
  @com.fasterxml.jackson.annotation.JsonIgnore
  private Pool pool;

  public Script() {
  }

  public Script(String script) {
    this.script = script;
  }

  public Script(Pool pool, String script) {
    this(script);
    this.pool = pool;
  }

  @JsonIgnore
  public Pool getPool() {
    return pool;
  }

  public void setPool(Pool pool) {
    this.pool = pool;
  }

  public void update(Script inScript) {
    script = inScript.script;
  }

  static String defaultScript;

  public static synchronized String createDefaultScript() {
    if (defaultScript == null) {
      ClassPathResource resource = new ClassPathResource("DefaultAnonymizer.js");
      try {
        defaultScript = IOUtils.toString(resource.getInputStream());
      } catch (Exception e) {
        logger.error("Error copying the anonymizer script", e);
      }
    }
    return defaultScript;
  }
}
