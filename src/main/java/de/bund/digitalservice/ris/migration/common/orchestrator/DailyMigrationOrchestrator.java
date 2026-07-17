package de.bund.digitalservice.ris.migration.common.orchestrator;

import de.bund.digitalservice.ris.migration.common.config.MigrationJobProperties;
import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.boot.CommandLineRunner;

/**
 * Drives the migration job, iterating over all pending days when running in daily mode. Projects
 * extend this class and annotate with {@code @Component}, injecting their concrete job bean.
 * Override {@link #preRunSetup(LocalDate)} for project-specific per-day setup.
 */
@RequiredArgsConstructor
@Slf4j
public class DailyMigrationOrchestrator implements CommandLineRunner {

  private final Job migrationJob;
  private final JobOperator jobOperator;
  private final Supplier<Optional<LocalDate>> lastDailyVersionSupplier;
  private final MigrationJobProperties properties;

  private final Clock clock = Clock.system(ZoneId.of("Europe/Berlin"));

  protected LocalDate today() {
    return LocalDate.now(clock);
  }

  @Override
  public void run(@Nonnull String... args) throws Exception {
    String migrationType = properties.migrationType();

    if (!"daily".equalsIgnoreCase(migrationType)) {
      JobExecution execution =
          jobOperator.start(
              migrationJob,
              new JobParametersBuilder()
                  .addLong("run.id", System.currentTimeMillis())
                  .toJobParameters());
      if (!ExitStatus.COMPLETED.equals(execution.getExitStatus())) {
        throw new IllegalStateException(
            "Migration failed. Exit status: " + execution.getExitStatus());
      }
      return;
    }

    LocalDate today = today();
    LocalDate lastRun = lastDailyVersionSupplier.get().orElse(today.minusDays(1));

    List<LocalDate> pendingDays = lastRun.plusDays(1).datesUntil(today.plusDays(1)).toList();

    if (pendingDays.isEmpty()) {
      log.info("No pending days to process.");
      return;
    }

    log.info(
        "Processing {} pending day(s): {} to {}",
        pendingDays.size(),
        pendingDays.getFirst(),
        pendingDays.getLast());

    for (LocalDate date : pendingDays) {
      log.info(StringUtils.repeat("#", 50));
      log.info("#{}#", StringUtils.center(String.format("Start migration for date: %s", date), 48));
      log.info(StringUtils.repeat("#", 50));

      clearDirectory(properties.inputDirectory());
      clearDirectory(properties.outputDirectory());
      preRunSetup(date);

      JobExecution execution =
          jobOperator.start(
              migrationJob,
              new JobParametersBuilder()
                  .addString("processingDate", date.toString())
                  .addLong("run.id", System.currentTimeMillis())
                  .toJobParameters());

      String exitCode = execution.getExitStatus().getExitCode();
      if (!ExitStatus.COMPLETED.getExitCode().equals(exitCode)
          && !"NO_DATA_FOUND".equals(exitCode)) {
        log.error(
            "Migration failed for date {}. Stopping. Exit status: {}",
            date,
            execution.getExitStatus());
        throw new IllegalStateException("Migration failed for date: " + date);
      }

      if ("NO_DATA_FOUND".equals(exitCode)) {
        log.info("No data found for date {}. Marking as processed and continuing.", date);
      } else {
        log.info("Successfully processed date: {}", date);
      }

      log.info(StringUtils.repeat("#", 50));
      log.info("#{}#", StringUtils.center(String.format("End migration for date: %s", date), 48));
      log.info(StringUtils.repeat("#", 50));
    }
  }

  /** Override to perform project-specific setup before each daily run (e.g. clearing caches). */
  protected void preRunSetup(LocalDate date) {
    // Intended to be overridden by subclasses for project-specific per-day setup.
  }

  private void clearDirectory(String directory) throws IOException {
    Path dir = Path.of(directory);
    if (!Files.exists(dir)) {
      return;
    }
    try (Stream<Path> entries = Files.walk(dir)) {
      entries
          .sorted(Comparator.reverseOrder())
          .filter(path -> !path.equals(dir))
          .forEach(
              path -> {
                try {
                  Files.delete(path);
                } catch (IOException e) {
                  log.warn("Could not delete {}: {}", path, e.getMessage());
                }
              });
    }
  }
}
