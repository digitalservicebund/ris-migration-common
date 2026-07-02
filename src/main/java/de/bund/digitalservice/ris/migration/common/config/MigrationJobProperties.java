package de.bund.digitalservice.ris.migration.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record MigrationJobProperties(
    Input input, Output output, String migrationType, int monthlyOffset) {

  public record Input(String directory) {}

  public record Output(String directory) {}

  public String inputDirectory() {
    return input.directory();
  }

  public String outputDirectory() {
    return output.directory();
  }
}
