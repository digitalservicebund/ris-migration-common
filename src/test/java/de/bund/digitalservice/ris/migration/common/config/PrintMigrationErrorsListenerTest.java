package de.bund.digitalservice.ris.migration.common.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.bund.digitalservice.ris.migration.common.model.CountedError;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.step.StepExecution;

@ExtendWith(MockitoExtension.class)
class PrintMigrationErrorsListenerTest {

  @Mock private Supplier<List<CountedError>> countedErrorsSupplier;
  @Mock private LongSupplier failedDocumentsCountSupplier;

  private PrintMigrationErrorsListener listener;

  @BeforeEach
  void setUp() {
    listener =
        new PrintMigrationErrorsListener(countedErrorsSupplier, failedDocumentsCountSupplier);
  }

  @Test
  void afterStep_noErrors_doesNotQueryFailedDocumentsCount() {
    when(countedErrorsSupplier.get()).thenReturn(List.of());
    var stepExecution = mock(StepExecution.class);

    listener.afterStep(stepExecution);

    verify(countedErrorsSupplier).get();
    verify(failedDocumentsCountSupplier, never()).getAsLong();
  }

  @Test
  void afterStep_withErrors_queriesFailedDocumentCount() {
    CountedError error =
        new CountedError() {
          @Override
          public Long getCount() {
            return 5L;
          }

          @Override
          public String getDescription() {
            return "Parsing failed";
          }
        };
    when(countedErrorsSupplier.get()).thenReturn(List.of(error));
    when(failedDocumentsCountSupplier.getAsLong()).thenReturn(3L);
    var stepExecution = mock(StepExecution.class);

    listener.afterStep(stepExecution);

    verify(failedDocumentsCountSupplier).getAsLong();
  }

  @Test
  void afterStep_returnsNull() {
    when(countedErrorsSupplier.get()).thenReturn(List.of());
    var stepExecution = mock(StepExecution.class);

    var result = listener.afterStep(stepExecution);

    org.assertj.core.api.Assertions.assertThat(result).isNull();
  }
}
