package de.bund.digitalservice.ris.migration.common.config;

import de.bund.digitalservice.ris.migration.common.repository.IncrementalMigrationStatusRepository;
import de.bund.digitalservice.ris.migration.common.repository.jpa.JpaIncrementalMigrationStatusRepository;
import de.bund.digitalservice.ris.migration.common.service.MigrationStatusService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.JpaTransactionManager;

/**
 * Enables {@link MigrationStatusService} for projects with a JPA datasource. {@link
 * JpaIncrementalMigrationStatusRepository} itself needs no explicit {@code @EnableJpaRepositories}
 * or manual bean here: {@link MigrationAutoConfiguration}'s {@code @AutoConfigurationPackage}
 * already registers this library's package with Spring Boot's {@code AutoConfigurationPackages}, so
 * the consuming project's own (auto-configured) repository scan picks it up alongside its own
 * repositories. Declaring {@code @EnableJpaRepositories} here instead would make Spring Boot back
 * off its repository-scanning auto-configuration for the *entire* context, silently breaking the
 * consuming project's own repository scanning.
 */
@AutoConfiguration(after = MigrationAutoConfiguration.class)
@ConditionalOnClass(JpaTransactionManager.class)
public class JpaMigrationAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public MigrationStatusService migrationStatusService(
      IncrementalMigrationStatusRepository statusRepository) {
    return new MigrationStatusService(statusRepository);
  }
}
