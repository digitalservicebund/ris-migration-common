package de.bund.digitalservice.ris.migration.common.config;

import de.bund.digitalservice.ris.migration.common.service.ChangeLogService;
import de.bund.digitalservice.ris.migration.common.service.ImportService;
import de.bund.digitalservice.ris.migration.common.service.MigrationStatusService;
import de.bund.digitalservice.ris.migration.common.service.MigrationStatusUpdater;
import de.bund.digitalservice.ris.migration.common.service.S3MigrationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Provides the profile-independent parts of a migration job. Each bean backs off when the project
 * declares its own, so a project can replace any single piece without giving up the rest.
 */
@AutoConfiguration
@EnableConfigurationProperties(MigrationJobProperties.class)
public class MigrationAutoConfiguration {

  /**
   * Shared changelog collector for the run.
   *
   * @return collector used by every step that publishes or removes a document
   */
  @Bean
  @ConditionalOnMissingBean
  public ChangeLogService changeLogService() {
    return new ChangeLogService();
  }

  /**
   * Wires the import step, which degrades to a no-op without S3.
   *
   * @param s3ServiceProvider resolves to an S3 client only under the {@code cloud} profile, which
   *     is what makes the import a no-op when running locally
   * @return the import step's entry point
   */
  @Bean
  @ConditionalOnMissingBean
  public ImportService importService(ObjectProvider<S3MigrationService> s3ServiceProvider) {
    return new ImportService(s3ServiceProvider);
  }

  /**
   * Only registered once the project supplies somewhere to persist the checkpoint.
   *
   * @param updater project-provided checkpoint persistence
   * @return the checkpoint-advancing service
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(MigrationStatusUpdater.class)
  public MigrationStatusService migrationStatusService(MigrationStatusUpdater updater) {
    return new MigrationStatusService(updater);
  }
}
