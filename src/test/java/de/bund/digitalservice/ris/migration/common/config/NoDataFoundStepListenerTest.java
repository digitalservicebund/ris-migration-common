package de.bund.digitalservice.ris.migration.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;

class NoDataFoundStepListenerTest {

  @Test
  void afterStep_noItemsRead_returnsNoDataFound() {
    var listener = new NoDataFoundStepListener();
    var stepExecution = mock(StepExecution.class);
    var jobExecution = mock(JobExecution.class);
    when(stepExecution.getJobExecution()).thenReturn(jobExecution);
    when(stepExecution.getReadCount()).thenReturn(0L);
    when(jobExecution.getStepExecutions()).thenReturn(List.of(stepExecution));

    ExitStatus result = listener.afterStep(stepExecution);

    assertThat(result.getExitCode()).isEqualTo("NO_DATA_FOUND");
  }

  @Test
  void afterStep_itemsRead_returnsCompleted() {
    var listener = new NoDataFoundStepListener();
    var stepExecution = mock(StepExecution.class);
    var jobExecution = mock(JobExecution.class);
    when(stepExecution.getJobExecution()).thenReturn(jobExecution);
    when(stepExecution.getReadCount()).thenReturn(5L);
    when(jobExecution.getStepExecutions()).thenReturn(List.of(stepExecution));

    ExitStatus result = listener.afterStep(stepExecution);

    assertThat(result).isEqualTo(ExitStatus.COMPLETED);
  }

  @Test
  void afterStep_companionStepReadItems_returnsCompleted() {
    var listener = new NoDataFoundStepListener("processXmlFilesStep");
    var stepExecution = mock(StepExecution.class);
    var companionExecution = mock(StepExecution.class);
    var jobExecution = mock(JobExecution.class);

    when(stepExecution.getJobExecution()).thenReturn(jobExecution);
    when(stepExecution.getReadCount()).thenReturn(0L);
    when(companionExecution.getStepName()).thenReturn("processXmlFilesStep");
    when(companionExecution.getReadCount()).thenReturn(3L);
    when(jobExecution.getStepExecutions()).thenReturn(List.of(stepExecution, companionExecution));

    ExitStatus result = listener.afterStep(stepExecution);

    assertThat(result).isEqualTo(ExitStatus.COMPLETED);
  }

  @Test
  void afterStep_companionStepAlsoEmpty_returnsNoDataFound() {
    var listener = new NoDataFoundStepListener("processXmlFilesStep");
    var stepExecution = mock(StepExecution.class);
    var companionExecution = mock(StepExecution.class);
    var jobExecution = mock(JobExecution.class);

    when(stepExecution.getJobExecution()).thenReturn(jobExecution);
    when(stepExecution.getReadCount()).thenReturn(0L);
    when(companionExecution.getStepName()).thenReturn("processXmlFilesStep");
    when(companionExecution.getReadCount()).thenReturn(0L);
    when(jobExecution.getStepExecutions()).thenReturn(List.of(stepExecution, companionExecution));

    ExitStatus result = listener.afterStep(stepExecution);

    assertThat(result.getExitCode()).isEqualTo("NO_DATA_FOUND");
  }
}
