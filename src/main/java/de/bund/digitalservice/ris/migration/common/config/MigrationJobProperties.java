package de.bund.digitalservice.ris.migration.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-project migration settings bound from the {@code app} property prefix.
 *
 * @param input local directory the import step fills and the reader consumes
 * @param output local directory the writer fills and the publish step uploads
 * @param migrationType cadence this deployment runs; not read by the library itself, so projects
 *     can dispatch on it in their own job configuration
 * @param monthlyOffset how many months back a monthly run may search for a usable dump before
 *     giving up
 */
@ConfigurationProperties(prefix = "app")
public record MigrationJobProperties(
    Input input, Output output, MigrationType migrationType, int monthlyOffset) {

  /**
   * Where the import step puts the files this run will migrate.
   *
   * @param directory local path source files are downloaded to
   */
  public record Input(String directory) {}

  /**
   * Where the writer puts the files this run will publish.
   *
   * @param directory local path migrated files are written to
   */
  public record Output(String directory) {}

  /**
   * Shorthand for the nested input directory.
   *
   * @return the configured input directory
   */
  public String inputDirectory() {
    return input.directory();
  }

  /**
   * Shorthand for the nested output directory.
   *
   * @return the configured output directory
   */
  public String outputDirectory() {
    return output.directory();
  }
}
