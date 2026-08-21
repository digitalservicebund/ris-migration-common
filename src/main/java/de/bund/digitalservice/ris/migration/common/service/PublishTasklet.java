package de.bund.digitalservice.ris.migration.common.service;

import de.bund.digitalservice.ris.migration.common.config.MigrationType;
import de.bund.digitalservice.ris.migration.common.config.MigrationTypeReader;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

/**
 * Uploads the output directory to the destination bucket and marks the step's exit status {@code
 * MONTHLY_MIGRATION_COMPLETED} or {@code DAILY_MIGRATION_COMPLETED} depending on {@code
 * migrationType} job parameter, so the surrounding job flow can branch on it (e.g. skip deletion
 * handling for monthly runs). No-op when {@code s3MigrationService} is {@code null} (local mode, no
 * cloud profile active).
 */
@Slf4j
@RequiredArgsConstructor
public class PublishTasklet implements Tasklet {

  /** Exit status after a full republish, on which the job flow can skip deletion handling. */
  public static final String MONTHLY_MIGRATION_COMPLETED = "MONTHLY_MIGRATION_COMPLETED";

  /** Exit status after a delta publish, on which the job flow continues into deletion handling. */
  public static final String DAILY_MIGRATION_COMPLETED = "DAILY_MIGRATION_COMPLETED";

  private final S3MigrationService s3MigrationService;
  private final String outputDirectory;

  /**
   * Uploads the output directory to the destination bucket and sets the exit status the job flow
   * branches on. Skips the upload in local mode.
   *
   * @param contribution step contribution the exit status is set on
   * @param chunkContext chunk context the {@code migrationType} job parameter is read from
   * @return {@link RepeatStatus#FINISHED}, since the upload runs once
   * @throws IOException if the upload fails
   */
  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext)
      throws IOException {
    MigrationType migrationType = MigrationTypeReader.read(chunkContext);
    if (s3MigrationService == null) {
      log.info("Local mode: skipping S3 publish");
    } else {
      log.info("Starting S3 publish from: {}", outputDirectory);
      s3MigrationService.uploadFolder(outputDirectory, migrationType);
    }
    contribution.setExitStatus(new ExitStatus(exitCode(migrationType)));
    return RepeatStatus.FINISHED;
  }

  private String exitCode(MigrationType migrationType) {
    return migrationType == MigrationType.MONTHLY
        ? MONTHLY_MIGRATION_COMPLETED
        : DAILY_MIGRATION_COMPLETED;
  }
}
