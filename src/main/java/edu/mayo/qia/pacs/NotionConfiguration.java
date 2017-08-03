package edu.mayo.qia.pacs;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.bazaarvoice.dropwizard.assets.AssetsBundleConfiguration;
import com.bazaarvoice.dropwizard.assets.AssetsConfiguration;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.secnod.dropwizard.shiro.ShiroConfiguration;

import edu.mayo.qia.pacs.configuration.EmailConfiguration;
import edu.mayo.qia.pacs.configuration.ServerConfiguration;
import io.dropwizard.Configuration;
import io.dropwizard.db.DataSourceFactory;

public class NotionConfiguration extends Configuration implements AssetsBundleConfiguration {

  @Valid
  @NotNull
  @JsonProperty
  private DataSourceFactory database = new DataSourceFactory();

  @Valid
  @NotNull
  @JsonProperty
  private final AssetsConfiguration assets = new AssetsConfiguration();

  @Valid
  @JsonProperty
  public String dbWeb = null;

  @Valid
  @JsonProperty
  public EmailConfiguration email;

  @Valid
  @NotNull
  @JsonProperty
  public ServerConfiguration notion = new ServerConfiguration();

  @Valid
  @JsonProperty
  public ShiroConfiguration shiro = new ShiroConfiguration();

  @Override
  public AssetsConfiguration getAssetsConfiguration() {
    return assets;
  }

  public DataSourceFactory getDataSourceFactory() {
    return database;
  }
}
