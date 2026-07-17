package de.bund.digitalservice.ris.migration.common.service;

import java.time.LocalDate;

/**
 * Persists the migration checkpoint. Projects implement this against their own
 * IncrementalMigrationStatus-shaped entity/repository and expose it as a bean.
 */
public interface MigrationStatusUpdater {
  void updateDaily(LocalDate date);

  void updateHistoricAndDaily(LocalDate date);
}
