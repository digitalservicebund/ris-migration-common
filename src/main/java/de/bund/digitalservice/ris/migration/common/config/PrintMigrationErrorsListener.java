package de.bund.digitalservice.ris.migration.common.config;

import de.bund.digitalservice.ris.migration.common.model.CountedError;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;

/**
 * Logs a table of the run's migration problems and how many documents failed, so an operator can
 * review a run's data quality without querying the database. Silent when the run had no errors.
 * Projects supply the two suppliers, since error storage is project-specific.
 */
@RequiredArgsConstructor
@Slf4j
public class PrintMigrationErrorsListener implements StepExecutionListener {

  private final Supplier<List<CountedError>> countedErrorsSupplier;
  private final LongSupplier failedDocumentsCountSupplier;

  /**
   * Logs the run's counted errors as a table together with the number of failed documents. Logs
   * nothing when the run had no errors.
   *
   * @param stepExecution execution of the step that just finished
   * @return {@code null} to leave the step's exit status unchanged
   */
  @Override
  public ExitStatus afterStep(@Nonnull StepExecution stepExecution) {
    var sb = new StringBuilder("\n");
    sb.append(StringUtils.repeat("-", 120));
    sb.append("\n");
    sb.append(StringUtils.rightPad(" Count", 6));
    sb.append(" | ");
    sb.append("Description");
    sb.append("\n");
    sb.append(StringUtils.repeat("-", 120));
    List<CountedError> countedErrors = countedErrorsSupplier.get();
    if (!countedErrors.isEmpty()) {
      for (CountedError countedError : countedErrors) {
        sb.append("\n")
            .append(StringUtils.leftPad(String.valueOf(countedError.getCount()), 6))
            .append(" | ")
            .append(countedError.getDescription());
      }
      sb.append("\n");
      sb.append(StringUtils.repeat("-", 120));
      long failedDocumentsCount = failedDocumentsCountSupplier.getAsLong();
      log.warn("Migration errors:{}\nFailed to migrate {} documents.", sb, failedDocumentsCount);
    }
    return null;
  }
}
