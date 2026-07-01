package de.bund.digitalservice.ris.migration.common.config;

import de.bund.digitalservice.ris.migration.common.service.ChangeLogService;
import de.bund.digitalservice.ris.migration.common.service.ImportService;
import de.bund.digitalservice.ris.migration.common.service.S3MigrationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
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
}
