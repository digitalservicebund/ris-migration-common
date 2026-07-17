package de.bund.digitalservice.ris.migration.common.orchestrator;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.bund.digitalservice.ris.migration.common.config.MigrationJobProperties;
import de.bund.digitalservice.ris.migration.common.config.MigrationJobProperties.Input;
import de.bund.digitalservice.ris.migration.common.config.MigrationJobProperties.Output;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;

@ExtendWith(MockitoExtension.class)
class DailyMigrationOrchestratorTest {

  @Mock private Job migrationJob;
  @Mock private JobOperator jobOperator;
  @Mock private Supplier<Optional<LocalDate>> lastDailyVersionSupplier;
  @Mock private JobExecution jobExecution;

  private static final LocalDate FIXED_TODAY = LocalDate.of(2025, Month.JUNE, 1);

  @Test
  void run_monthlyMode_runsJobOnce(@TempDir Path input, @TempDir Path output) throws Exception {
    var properties = buildProperties("monthly", input.toString(), output.toString());
    var orchestrator =
        new DailyMigrationOrchestrator(
            migrationJob, jobOperator, lastDailyVersionSupplier, properties);

    when(jobOperator.start(eq(migrationJob), any())).thenReturn(jobExecution);
    when(jobExecution.getExitStatus()).thenReturn(ExitStatus.COMPLETED);

    orchestrator.run();

    verify(jobOperator, times(1)).start(eq(migrationJob), any());
  }

  @Test
  void run_monthlyMode_failedJob_throwsIllegalState(@TempDir Path input, @TempDir Path output)
      throws Exception {
    var properties = buildProperties("monthly", input.toString(), output.toString());
    var orchestrator =
        new DailyMigrationOrchestrator(
            migrationJob, jobOperator, lastDailyVersionSupplier, properties);

    when(jobOperator.start(eq(migrationJob), any())).thenReturn(jobExecution);
    when(jobExecution.getExitStatus()).thenReturn(ExitStatus.FAILED);

    assertThatThrownBy(orchestrator::run).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void run_dailyMode_noPendingDays_doesNotStartJob(@TempDir Path input, @TempDir Path output)
      throws Exception {
    var properties = buildProperties("daily", input.toString(), output.toString());
    var orchestrator =
        new DailyMigrationOrchestrator(
            migrationJob, jobOperator, lastDailyVersionSupplier, properties) {
          @Override
          protected LocalDate today() {
            return FIXED_TODAY;
          }
        };

    when(lastDailyVersionSupplier.get()).thenReturn(Optional.of(FIXED_TODAY));

    orchestrator.run();

    verify(jobOperator, never())
        .start(
            any(org.springframework.batch.core.job.Job.class),
            any(org.springframework.batch.core.job.parameters.JobParameters.class));
  }

  @Test
  void run_dailyMode_withPendingDays_runsJobPerDay(@TempDir Path input, @TempDir Path output)
      throws Exception {
    var properties = buildProperties("daily", input.toString(), output.toString());
    var orchestrator =
        new DailyMigrationOrchestrator(
            migrationJob, jobOperator, lastDailyVersionSupplier, properties) {
          @Override
          protected LocalDate today() {
            return FIXED_TODAY;
          }
        };

    when(lastDailyVersionSupplier.get()).thenReturn(Optional.of(FIXED_TODAY.minusDays(2)));
    when(jobOperator.start(eq(migrationJob), any())).thenReturn(jobExecution);
    when(jobExecution.getExitStatus()).thenReturn(ExitStatus.COMPLETED);

    orchestrator.run();

    verify(jobOperator, times(2)).start(eq(migrationJob), any());
  }

  @Test
  void run_dailyMode_noStatusAvailable_defaultsToYesterday(
      @TempDir Path input, @TempDir Path output) throws Exception {
    var properties = buildProperties("daily", input.toString(), output.toString());
    var orchestrator =
        new DailyMigrationOrchestrator(
            migrationJob, jobOperator, lastDailyVersionSupplier, properties);

    when(lastDailyVersionSupplier.get()).thenReturn(Optional.empty());
    when(jobOperator.start(eq(migrationJob), any())).thenReturn(jobExecution);
    when(jobExecution.getExitStatus()).thenReturn(ExitStatus.COMPLETED);

    orchestrator.run();

    verify(jobOperator, times(1)).start(eq(migrationJob), any());
  }

  @Test
  void run_dailyMode_noDataFoundExitCode_continuesProcessing(
      @TempDir Path input, @TempDir Path output) throws Exception {
    var properties = buildProperties("daily", input.toString(), output.toString());
    var orchestrator =
        new DailyMigrationOrchestrator(
            migrationJob, jobOperator, lastDailyVersionSupplier, properties);

    when(lastDailyVersionSupplier.get()).thenReturn(Optional.empty());
    when(jobOperator.start(eq(migrationJob), any())).thenReturn(jobExecution);
    when(jobExecution.getExitStatus()).thenReturn(new ExitStatus("NO_DATA_FOUND"));

    orchestrator.run();

    verify(jobOperator, times(1)).start(eq(migrationJob), any());
  }

  @Test
  void run_dailyMode_failedJob_throwsIllegalState(@TempDir Path input, @TempDir Path output)
      throws Exception {
    var properties = buildProperties("daily", input.toString(), output.toString());
    var orchestrator =
        new DailyMigrationOrchestrator(
            migrationJob, jobOperator, lastDailyVersionSupplier, properties);

    when(lastDailyVersionSupplier.get()).thenReturn(Optional.empty());
    when(jobOperator.start(eq(migrationJob), any())).thenReturn(jobExecution);
    when(jobExecution.getExitStatus()).thenReturn(ExitStatus.FAILED);

    assertThatThrownBy(orchestrator::run).isInstanceOf(IllegalStateException.class);
  }

  private static MigrationJobProperties buildProperties(
      String migrationType, String inputDir, String outputDir) {
    return new MigrationJobProperties(
        new Input(inputDir), new Output(outputDir), migrationType, 12);
  }
}
