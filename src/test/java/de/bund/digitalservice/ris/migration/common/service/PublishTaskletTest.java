package de.bund.digitalservice.ris.migration.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import de.bund.digitalservice.ris.migration.common.config.MigrationType;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.batch.test.MetaDataInstanceFactory;

class PublishTaskletTest {

  @Test
  void execute_dailyMigration_uploadsAndSetsDailyExitStatus() throws Exception {
    var s3MigrationService = mock(S3MigrationService.class);
    var tasklet = new PublishTasklet(s3MigrationService, "/output");
    StepExecution stepExecution =
        MetaDataInstanceFactory.createStepExecution(
            new JobParametersBuilder()
                .addString("migrationType", MigrationType.DAILY.name())
                .toJobParameters());
    StepContribution stepContribution = new StepContribution(stepExecution);
    ChunkContext chunkContext = new ChunkContext(new StepContext(stepExecution));

    RepeatStatus result = tasklet.execute(stepContribution, chunkContext);

    verify(s3MigrationService).uploadFolder("/output", MigrationType.DAILY);
    assertThat(stepContribution.getExitStatus())
        .isEqualTo(new ExitStatus(PublishTasklet.DAILY_MIGRATION_COMPLETED));
    assertThat(result).isEqualTo(RepeatStatus.FINISHED);
  }

  @Test
  void execute_monthlyMigration_uploadsAndSetsMonthlyExitStatus() throws Exception {
    var s3MigrationService = mock(S3MigrationService.class);
    var tasklet = new PublishTasklet(s3MigrationService, "/output");
    StepExecution stepExecution =
        MetaDataInstanceFactory.createStepExecution(
            new JobParametersBuilder()
                .addString("migrationType", MigrationType.MONTHLY.name())
                .toJobParameters());
    StepContribution stepContribution = new StepContribution(stepExecution);
    ChunkContext chunkContext = new ChunkContext(new StepContext(stepExecution));

    tasklet.execute(stepContribution, chunkContext);

    verify(s3MigrationService).uploadFolder("/output", MigrationType.MONTHLY);
    assertThat(stepContribution.getExitStatus())
        .isEqualTo(new ExitStatus(PublishTasklet.MONTHLY_MIGRATION_COMPLETED));
  }

  @Test
  void execute_nullS3Service_skipsUploadButStillSetsExitStatus() throws Exception {
    var tasklet = new PublishTasklet(null, "/output");
    StepExecution stepExecution =
        MetaDataInstanceFactory.createStepExecution(
            new JobParametersBuilder()
                .addString("migrationType", MigrationType.DAILY.name())
                .toJobParameters());
    StepContribution stepContribution = new StepContribution(stepExecution);
    ChunkContext chunkContext = new ChunkContext(new StepContext(stepExecution));

    RepeatStatus result = tasklet.execute(stepContribution, chunkContext);

    assertThat(stepContribution.getExitStatus())
        .isEqualTo(new ExitStatus(PublishTasklet.DAILY_MIGRATION_COMPLETED));
    assertThat(result).isEqualTo(RepeatStatus.FINISHED);
  }
}
