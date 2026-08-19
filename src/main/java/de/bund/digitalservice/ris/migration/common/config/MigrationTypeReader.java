package de.bund.digitalservice.ris.migration.common.config;

import lombok.experimental.UtilityClass;
import org.springframework.batch.core.scope.context.ChunkContext;

/**
 * Migration type reader class for reading the migration type value from a Spring Batch {@link
 * org.springframework.batch.core.scope.context.ChunkContext}.
 */
@UtilityClass
public class MigrationTypeReader {

  /**
   * Reads the {@code migrationType} job parameter written by {@code DailyMigrationOrchestrator}.
   *
   * @param chunkContext current step's chunk context
   * @return migration type for the running job
   */
  public static MigrationType read(ChunkContext chunkContext) {
    return MigrationType.valueOf(
        chunkContext
            .getStepContext()
            .getStepExecution()
            .getJobParameters()
            .getString("migrationType"));
  }
}
