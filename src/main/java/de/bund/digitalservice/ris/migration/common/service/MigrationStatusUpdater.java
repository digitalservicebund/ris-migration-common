package de.bund.digitalservice.ris.migration.common.service;

import java.time.LocalDate;

/**
 * Persists the migration checkpoint. Projects implement this against their own
 * IncrementalMigrationStatus-shaped entity/repository and expose it as a bean.
 */
public interface MigrationStatusUpdater {
  /**
   * Records that daily exports up to this date have been migrated.
   *
   * @param date last processed export date
   */
  void updateDaily(LocalDate date);

  /**
   * Records a completed full migration and moves the daily checkpoint to the same date, so later
   * daily runs resume from the dump instead of re-migrating what it already covered.
   *
   * @param date baseline established by the monthly dump
   */
  void updateHistoricAndDaily(LocalDate date);
}
