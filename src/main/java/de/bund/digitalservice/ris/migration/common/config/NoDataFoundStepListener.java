package de.bund.digitalservice.ris.migration.common.config;

import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;

/**
 * Sets exit status {@code NO_DATA_FOUND} when no items were read by this step, nor by any of the
 * {@code companionStepNames} sharing the same job execution (e.g. a companion JSON step run
 * alongside an XML step), so the surrounding job flow can branch straight to checkpointing.
 */
@Slf4j
public class NoDataFoundStepListener implements StepExecutionListener {

  public static final String NO_DATA_FOUND = "NO_DATA_FOUND";

  private final Set<String> companionStepNames;

  public NoDataFoundStepListener(String... companionStepNames) {
    this.companionStepNames = Set.of(companionStepNames);
  }

  @Override
  public ExitStatus afterStep(@Nonnull StepExecution stepExecution) {
    long companionReadCount =
        stepExecution.getJobExecution().getStepExecutions().stream()
            .filter(
                execution ->
                    execution.getStepName() != null
                        && companionStepNames.contains(execution.getStepName()))
            .mapToLong(StepExecution::getReadCount)
            .sum();

    if (stepExecution.getReadCount() + companionReadCount == 0) {
      log.info("No files processed. Terminating migration.");
      return new ExitStatus(NO_DATA_FOUND);
    }
    return ExitStatus.COMPLETED;
  }
}
