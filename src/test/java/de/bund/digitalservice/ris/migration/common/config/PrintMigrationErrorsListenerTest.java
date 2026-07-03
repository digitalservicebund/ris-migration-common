package de.bund.digitalservice.ris.migration.common.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.bund.digitalservice.ris.migration.common.model.CountedError;
import de.bund.digitalservice.ris.migration.common.model.MigrationStatus;
import de.bund.digitalservice.ris.migration.common.repository.MigrationErrorRepository;
import de.bund.digitalservice.ris.migration.common.repository.MigrationRecordRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.step.StepExecution;

@ExtendWith(MockitoExtension.class)
class PrintMigrationErrorsListenerTest {

  @Mock private MigrationErrorRepository migrationErrorRepository;
  @Mock private MigrationRecordRepository migrationRecordRepository;

  private PrintMigrationErrorsListener listener;

  @BeforeEach
  void setUp() {
    listener =
        new PrintMigrationErrorsListener(migrationErrorRepository, migrationRecordRepository);
  }

  @Test
  void afterStep_noErrors_doesNotQueryRecordRepository() {
    when(migrationErrorRepository.countAllGroupByDescription()).thenReturn(List.of());
    var stepExecution = mock(StepExecution.class);

    listener.afterStep(stepExecution);

    verify(migrationErrorRepository).countAllGroupByDescription();
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
    when(migrationErrorRepository.countAllGroupByDescription()).thenReturn(List.of(error));
    when(migrationRecordRepository.countAllByMigrationStatusNot(
            MigrationStatus.TRANSFORMATION_SUCCEEDED))
        .thenReturn(3L);
    var stepExecution = mock(StepExecution.class);

    listener.afterStep(stepExecution);

    verify(migrationRecordRepository)
        .countAllByMigrationStatusNot(MigrationStatus.TRANSFORMATION_SUCCEEDED);
  }

  @Test
  void afterStep_returnsNull() {
    when(migrationErrorRepository.countAllGroupByDescription()).thenReturn(List.of());
    var stepExecution = mock(StepExecution.class);

    var result = listener.afterStep(stepExecution);

    org.assertj.core.api.Assertions.assertThat(result).isNull();
  }
}
