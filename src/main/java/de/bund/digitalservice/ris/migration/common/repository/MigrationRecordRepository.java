package de.bund.digitalservice.ris.migration.common.repository;

import de.bund.digitalservice.ris.migration.common.model.MigrationStatus;

public interface MigrationRecordRepository {
  long countAllByMigrationStatusNot(MigrationStatus status);
}
