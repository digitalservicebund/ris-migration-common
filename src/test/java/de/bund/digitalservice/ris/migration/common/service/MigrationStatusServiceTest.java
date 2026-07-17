package de.bund.digitalservice.ris.migration.common.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.Month;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.ExecutionContext;

@ExtendWith(MockitoExtension.class)
class MigrationStatusServiceTest {

  @Mock private MigrationStatusUpdater updater;

  private MigrationStatusService service;

  @BeforeEach
  void setUp() {
    service = new MigrationStatusService(updater);
  }

  @Test
  void updateStatus_daily_delegatesToUpdaterWithDailyVersion() {
    var context = new ExecutionContext();
    context.put("newDailyVersion", LocalDate.of(2025, Month.JANUARY, 15));

    service.updateStatus(context, "daily");

    verify(updater).updateDaily(LocalDate.of(2025, Month.JANUARY, 15));
    verify(updater, never()).updateHistoricAndDaily(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void updateStatus_monthly_delegatesToUpdaterWithHistoricVersion() {
    var context = new ExecutionContext();
    context.put("newHistoricVersion", LocalDate.of(2024, Month.DECEMBER, 26));

    service.updateStatus(context, "monthly");

    verify(updater).updateHistoricAndDaily(LocalDate.of(2024, Month.DECEMBER, 26));
    verify(updater, never()).updateDaily(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void updateStatus_noContextKeys_skipsUpdate() {
    service.updateStatus(new ExecutionContext(), "daily");

    verify(updater, never()).updateDaily(org.mockito.ArgumentMatchers.any());
    verify(updater, never()).updateHistoricAndDaily(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void updateStatus_monthlyMigrationType_dailyVersionKeyPresent_ignoresDailyKey() {
    // "newDailyVersion" is only honored when migrationType is "daily" (see
    // updateStatus_daily_delegatesToUpdaterWithDailyVersion); a monthly run with no historic key
    // present must skip the update even if a stray daily key exists in the context.
    var context = new ExecutionContext();
    context.put("newDailyVersion", LocalDate.of(2025, Month.JANUARY, 15));

    service.updateStatus(context, "monthly");

    verify(updater, never()).updateDaily(org.mockito.ArgumentMatchers.any());
    verify(updater, never()).updateHistoricAndDaily(org.mockito.ArgumentMatchers.any());
  }
}
