package de.bund.digitalservice.ris.migration.common.config;

import de.bund.digitalservice.ris.migration.common.repository.IncrementalMigrationStatusRepository;
import de.bund.digitalservice.ris.migration.common.repository.jpa.JpaIncrementalMigrationStatusRepository;
import de.bund.digitalservice.ris.migration.common.service.MigrationStatusService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;

/**
 * Registers {@link JpaIncrementalMigrationStatusRepository} as a Spring Data bean so projects with
 * a JPA datasource get daily-mode checkpoint persistence without declaring the adapter themselves.
 */
@AutoConfiguration(after = MigrationAutoConfiguration.class)
@ConditionalOnClass(JpaTransactionManager.class)
@EnableJpaRepositories(basePackageClasses = JpaIncrementalMigrationStatusRepository.class)
public class JpaMigrationAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public MigrationStatusService migrationStatusService(
      IncrementalMigrationStatusRepository statusRepository) {
    return new MigrationStatusService(statusRepository);
  }
}
