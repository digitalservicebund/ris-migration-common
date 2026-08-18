package de.bund.digitalservice.ris.migration.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.test.MetaDataInstanceFactory;

class MigrationTypeReaderTest {

  @Test
  void read() {
    // given
    StepContext stepContext =
        new StepContext(
            MetaDataInstanceFactory.createStepExecution(
                new JobParametersBuilder()
                    .addString("migrationType", MigrationType.DAILY.name())
                    .toJobParameters()));
    ChunkContext chunkContext = new ChunkContext(stepContext);

    // when
    MigrationType migrationType = MigrationTypeReader.read(chunkContext);

    // then
    assertThat(migrationType).isEqualTo(MigrationType.DAILY);
  }
}
