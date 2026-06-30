package de.bund.digitalservice.ris.migration.common.repository;

import de.bund.digitalservice.ris.migration.common.domain.IncrementalMigrationStatus;
import java.util.Optional;

public interface IncrementalMigrationStatusRepository {
	Optional<IncrementalMigrationStatus> findFirstByOrderByCreatedAtDesc();

	IncrementalMigrationStatus save(IncrementalMigrationStatus status);
}
