package de.bund.digitalservice.ris.migration.common.repository.jpa;

import de.bund.digitalservice.ris.migration.common.domain.IncrementalMigrationStatus;
import de.bund.digitalservice.ris.migration.common.repository.IncrementalMigrationStatusRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data adapter exposing {@link IncrementalMigrationStatus} as the {@link
 * IncrementalMigrationStatusRepository} bean consumed by {@code DailyMigrationOrchestrator} and
 * {@code MigrationStatusService}, so projects no longer need to hand-write it.
 */
public interface JpaIncrementalMigrationStatusRepository
    extends JpaRepository<IncrementalMigrationStatus, UUID>, IncrementalMigrationStatusRepository {

  @Override
  IncrementalMigrationStatus save(IncrementalMigrationStatus status);
}
