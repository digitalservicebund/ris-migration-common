package de.bund.digitalservice.ris.migration.common.service;

import de.bund.digitalservice.ris.migration.common.config.MigrationType;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MigrationStatusService {

  private final MigrationStatusUpdater updater;

  private static final String DAILY_VERSION_KEY = "newDailyVersion";
  private static final String HISTORIC_VERSION_KEY = "newHistoricVersion";

  /**
   * Records the import version the finished run reached. A monthly run also moves the daily
   * checkpoint to its baseline, so subsequent daily runs pick up from the new dump instead of
   * re-migrating the months it already covered. Does nothing when the run produced no new version,
   * for instance because there was nothing to import.
   *
   * @param context step context carrying the version written by {@link ImportService}
   * @param migrationType cadence that produced the run
   */
  public void updateStatus(ExecutionContext context, MigrationType migrationType) {
    boolean hasDailyVersion =
        migrationType == MigrationType.DAILY && context.containsKey(DAILY_VERSION_KEY);
    boolean hasHistoricVersion = context.containsKey(HISTORIC_VERSION_KEY);
    if (!hasDailyVersion && !hasHistoricVersion) {
      log.debug("No new import version to record, skipping migration status update.");
      return;
    }
    if (hasDailyVersion) {
      LocalDate dailyDate = (LocalDate) context.get(DAILY_VERSION_KEY);
      log.info("Updating status with daily version: {}", dailyDate);
      updater.updateDaily(dailyDate);
    } else {
      LocalDate monthlyDate = (LocalDate) context.get(HISTORIC_VERSION_KEY);
      log.info("Migration completed. Bridging daily version to: {}", monthlyDate);
      updater.updateHistoricAndDaily(monthlyDate);
    }
  }
}
