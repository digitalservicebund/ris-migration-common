package de.bund.digitalservice.ris.migration.common.service;

import de.bund.digitalservice.ris.migration.common.config.MigrationType;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the import step. In the {@code cloud} profile an {@link S3MigrationService} bean is
 * present, so data is downloaded from S3. In local mode the import is a no-op: later steps consume
 * whatever files already sit in the input directory.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ImportService {

  private final ObjectProvider<S3MigrationService> s3ServiceProvider;

  private static final String DAILY_VERSION_KEY = "newDailyVersion";
  private static final String HISTORIC_VERSION_KEY = "newHistoricVersion";

  /**
   * Makes the source files for this run available in the input directory and records the checkpoint
   * the run should persist on success.
   *
   * <p>A daily run downloads one export day and requires it to exist. Any other cadence downloads
   * the most recent monthly dump and records a baseline that lets later daily runs bridge the gap
   * back to the present.
   *
   * @param context step context the resulting import version is written to, for {@link
   *     MigrationStatusService} to persist once the run completes
   * @param migrationType cadence being run
   * @param inputDirectory local directory the source files are downloaded to
   * @param processingDate date the run is executed for
   * @throws FileNotFoundException if a daily run finds no export for its date
   */
  public void importData(
      ExecutionContext context,
      MigrationType migrationType,
      String inputDirectory,
      LocalDate processingDate)
      throws FileNotFoundException {

    S3MigrationService s3Service = s3ServiceProvider.getIfAvailable();
    if (s3Service == null) {
      try (var files = Files.list(Path.of(inputDirectory))) {
        long count = files.filter(Files::isRegularFile).count();
        log.info("Local mode: {} file(s) available in {}", count, inputDirectory);
      } catch (IOException e) {
        log.warn("Local mode: could not count files in {}: {}", inputDirectory, e.getMessage());
      }
      return;
    }

    if (migrationType == MigrationType.DAILY) {
      log.info("Starting daily import for date: {}", processingDate);
      String sourcePath = s3Service.resolveDailySourcePath(processingDate);
      s3Service.assertFolderExists(sourcePath);
      s3Service.downloadFolder(sourcePath, inputDirectory);
      context.put(DAILY_VERSION_KEY, processingDate);
    } else {
      log.info("Starting historic import");
      S3MigrationService.MonthlyImportSource monthly =
          s3Service.resolveMonthlyPrefix(processingDate);
      s3Service.downloadFolder(monthly.prefix(), inputDirectory);
      log.info(
          "Setting daily baseline to {} (5 days before end of the month before)",
          monthly.baseline());
      context.put(HISTORIC_VERSION_KEY, monthly.baseline());
    }
  }
}
