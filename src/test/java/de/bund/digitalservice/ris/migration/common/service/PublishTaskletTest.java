package de.bund.digitalservice.ris.migration.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

class PublishTaskletTest {

  @Test
  void execute_dailyMigration_uploadsAndSetsDailyExitStatus() throws Exception {
    var s3MigrationService = mock(S3MigrationService.class);
    var tasklet = new PublishTasklet(s3MigrationService, "/output", "daily");
    var contribution = mock(StepContribution.class);
    var chunkContext = mock(ChunkContext.class);

    RepeatStatus result = tasklet.execute(contribution, chunkContext);

    verify(s3MigrationService).uploadFolder("/output", "daily");
    verify(contribution).setExitStatus(new ExitStatus(PublishTasklet.DAILY_MIGRATION_COMPLETED));
    assertThat(result).isEqualTo(RepeatStatus.FINISHED);
  }

  @Test
  void execute_monthlyMigration_uploadsAndSetsMonthlyExitStatus() throws Exception {
    var s3MigrationService = mock(S3MigrationService.class);
    var tasklet = new PublishTasklet(s3MigrationService, "/output", "monthly");
    var contribution = mock(StepContribution.class);
    var chunkContext = mock(ChunkContext.class);

    tasklet.execute(contribution, chunkContext);

    verify(s3MigrationService).uploadFolder("/output", "monthly");
    verify(contribution).setExitStatus(new ExitStatus(PublishTasklet.MONTHLY_MIGRATION_COMPLETED));
  }

  @Test
  void execute_nullS3Service_skipsUploadButStillSetsExitStatus() throws Exception {
    var tasklet = new PublishTasklet(null, "/output", "daily");
    var contribution = mock(StepContribution.class);
    var chunkContext = mock(ChunkContext.class);

    RepeatStatus result = tasklet.execute(contribution, chunkContext);

    verify(contribution).setExitStatus(new ExitStatus(PublishTasklet.DAILY_MIGRATION_COMPLETED));
    assertThat(result).isEqualTo(RepeatStatus.FINISHED);
  }
}
