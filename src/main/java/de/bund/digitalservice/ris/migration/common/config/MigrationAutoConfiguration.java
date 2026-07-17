package de.bund.digitalservice.ris.migration.common.config;

import de.bund.digitalservice.ris.migration.common.service.ChangeLogService;
import de.bund.digitalservice.ris.migration.common.service.ImportService;
import de.bund.digitalservice.ris.migration.common.service.MigrationStatusService;
import de.bund.digitalservice.ris.migration.common.service.MigrationStatusUpdater;
import de.bund.digitalservice.ris.migration.common.service.S3MigrationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigurationPackage(basePackages = "de.bund.digitalservice.ris.migration.common")
@EnableConfigurationProperties(MigrationJobProperties.class)
public class MigrationAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ChangeLogService changeLogService() {
    return new ChangeLogService();
  }

  @Bean
  @ConditionalOnMissingBean
  public ImportService importService(ObjectProvider<S3MigrationService> s3ServiceProvider) {
    return new ImportService(s3ServiceProvider);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(MigrationStatusUpdater.class)
  public MigrationStatusService migrationStatusService(MigrationStatusUpdater updater) {
    return new MigrationStatusService(updater);
  }
}
