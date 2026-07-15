package de.bund.digitalservice.ris.migration.common.service;

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
 * migrationType}, so the surrounding job flow can branch on it (e.g. skip deletion handling for
 * monthly runs). No-op when {@code s3MigrationService} is {@code null} (local mode, no cloud
 * profile active).
 */
@Slf4j
@RequiredArgsConstructor
public class PublishTasklet implements Tasklet {

  public static final String MONTHLY_MIGRATION_COMPLETED = "MONTHLY_MIGRATION_COMPLETED";
  public static final String DAILY_MIGRATION_COMPLETED = "DAILY_MIGRATION_COMPLETED";

  private final S3MigrationService s3MigrationService;
  private final String outputDirectory;
  private final String migrationType;

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext)
      throws IOException {
    if (s3MigrationService == null) {
      log.info("Local mode: skipping S3 publish");
    } else {
      log.info("Starting S3 publish from: {}", outputDirectory);
      s3MigrationService.uploadFolder(outputDirectory, migrationType);
    }
    contribution.setExitStatus(new ExitStatus(exitCode()));
    return RepeatStatus.FINISHED;
  }

  private String exitCode() {
    return "monthly".equalsIgnoreCase(migrationType)
        ? MONTHLY_MIGRATION_COMPLETED
        : DAILY_MIGRATION_COMPLETED;
  }
}
