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
import de.bund.digitalservice.ris.migration.common.domain.IncrementalMigrationStatus;
import de.bund.digitalservice.ris.migration.common.repository.IncrementalMigrationStatusRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Path;

@ExtendWith(MockitoExtension.class)
class DailyMigrationOrchestratorTest {

	@Mock
	private Job migrationJob;
	@Mock
	private JobOperator jobOperator;
	@Mock
	private ObjectProvider<IncrementalMigrationStatusRepository> statusRepoProvider;
	@Mock
	private IncrementalMigrationStatusRepository statusRepo;
	@Mock
	private JobExecution jobExecution;

	@Test
	void run_monthlyMode_runsJobOnce(@TempDir Path input, @TempDir Path output) throws Exception {
		var properties = buildProperties("monthly", input.toString(), output.toString());
		var orchestrator = new DailyMigrationOrchestrator(migrationJob, jobOperator, statusRepoProvider, properties);

		when(jobOperator.start(eq(migrationJob), any())).thenReturn(jobExecution);
		when(jobExecution.getExitStatus()).thenReturn(ExitStatus.COMPLETED);

		orchestrator.run();

		verify(jobOperator, times(1)).start(eq(migrationJob), any());
	}

	@Test
	void run_monthlyMode_failedJob_throwsIllegalState(@TempDir Path input, @TempDir Path output) throws Exception {
		var properties = buildProperties("monthly", input.toString(), output.toString());
		var orchestrator = new DailyMigrationOrchestrator(migrationJob, jobOperator, statusRepoProvider, properties);

		when(jobOperator.start(eq(migrationJob), any())).thenReturn(jobExecution);
		when(jobExecution.getExitStatus()).thenReturn(ExitStatus.FAILED);

		assertThatThrownBy(orchestrator::run).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void run_dailyMode_noPendingDays_doesNotStartJob(@TempDir Path input, @TempDir Path output) throws Exception {
		var properties = buildProperties("daily", input.toString(), output.toString());
		var orchestrator = new DailyMigrationOrchestrator(migrationJob, jobOperator, statusRepoProvider, properties);

		when(statusRepoProvider.getIfAvailable()).thenReturn(statusRepo);
		LocalDate today = LocalDate.now();
		when(statusRepo.findFirstByOrderByCreatedAtDesc())
				.thenReturn(Optional.of(IncrementalMigrationStatus.builder().lastDailyImportVersion(today).build()));

		orchestrator.run();

		verify(jobOperator, never()).start(any(org.springframework.batch.core.job.Job.class), any(org.springframework.batch.core.job.parameters.JobParameters.class));
	}

	@Test
	void run_dailyMode_withPendingDays_runsJobPerDay(@TempDir Path input, @TempDir Path output) throws Exception {
		var properties = buildProperties("daily", input.toString(), output.toString());
		var orchestrator = new DailyMigrationOrchestrator(migrationJob, jobOperator, statusRepoProvider, properties);

		when(statusRepoProvider.getIfAvailable()).thenReturn(statusRepo);
		LocalDate today = LocalDate.now();
		when(statusRepo.findFirstByOrderByCreatedAtDesc()).thenReturn(
				Optional.of(IncrementalMigrationStatus.builder().lastDailyImportVersion(today.minusDays(2)).build()));
		when(jobOperator.start(eq(migrationJob), any())).thenReturn(jobExecution);
		when(jobExecution.getExitStatus()).thenReturn(ExitStatus.COMPLETED);

		orchestrator.run();

		verify(jobOperator, times(2)).start(eq(migrationJob), any());
	}

	@Test
	void run_dailyMode_noStatusRepo_defaultsToYesterday(@TempDir Path input, @TempDir Path output) throws Exception {
		var properties = buildProperties("daily", input.toString(), output.toString());
		var orchestrator = new DailyMigrationOrchestrator(migrationJob, jobOperator, statusRepoProvider, properties);

		when(statusRepoProvider.getIfAvailable()).thenReturn(null);
		when(jobOperator.start(eq(migrationJob), any())).thenReturn(jobExecution);
		when(jobExecution.getExitStatus()).thenReturn(ExitStatus.COMPLETED);

		orchestrator.run();

		verify(jobOperator, times(1)).start(eq(migrationJob), any());
	}

	@Test
	void run_dailyMode_noDataFoundExitCode_continuesProcessing(@TempDir Path input, @TempDir Path output)
			throws Exception {
		var properties = buildProperties("daily", input.toString(), output.toString());
		var orchestrator = new DailyMigrationOrchestrator(migrationJob, jobOperator, statusRepoProvider, properties);

		when(statusRepoProvider.getIfAvailable()).thenReturn(null);
		when(jobOperator.start(eq(migrationJob), any())).thenReturn(jobExecution);
		when(jobExecution.getExitStatus()).thenReturn(new ExitStatus("NO_DATA_FOUND"));

		orchestrator.run();

		verify(jobOperator, times(1)).start(eq(migrationJob), any());
	}

	@Test
	void run_dailyMode_failedJob_throwsIllegalState(@TempDir Path input, @TempDir Path output) throws Exception {
		var properties = buildProperties("daily", input.toString(), output.toString());
		var orchestrator = new DailyMigrationOrchestrator(migrationJob, jobOperator, statusRepoProvider, properties);

		when(statusRepoProvider.getIfAvailable()).thenReturn(null);
		when(jobOperator.start(eq(migrationJob), any())).thenReturn(jobExecution);
		when(jobExecution.getExitStatus()).thenReturn(ExitStatus.FAILED);

		assertThatThrownBy(orchestrator::run).isInstanceOf(IllegalStateException.class);
	}

	private static MigrationJobProperties buildProperties(String migrationType, String inputDir, String outputDir) {
		return new MigrationJobProperties(new Input(inputDir, "xml"), new Output(outputDir), migrationType,
				LocalDate.of(2020, 1, 1));
	}
}
