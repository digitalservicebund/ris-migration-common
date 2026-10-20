package de.bund.digitalservice.ris.migration.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.repository.support.ResourcelessJobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class PrintProcessedItemsChunkListenerTest {

  @Test
  void afterChunk_emptyChunk_doesNotThrow() {
    var listener = new PrintProcessedItemsChunkListener<String, String>();
    assertThatCode(() -> listener.afterChunk(new Chunk<>(List.of()))).doesNotThrowAnyException();
  }

  @Test
  void afterChunk_multipleInvocations_logsRunningTotal(CapturedOutput output) {
    var listener = new PrintProcessedItemsChunkListener<String, String>();
    var chunk = new Chunk<>(List.of("a", "b"));

    listener.afterChunk(chunk);
    listener.afterChunk(chunk);

    assertThat(output.getOut()).containsSubsequence("Processed 2 items.", "Processed 4 items.");
  }

  @Test
  void beforeStep_resetsRunningTotal(CapturedOutput output) {
    var listener = new PrintProcessedItemsChunkListener<String, String>();
    var chunk = new Chunk<>(List.of("a", "b"));

    listener.afterChunk(chunk);
    listener.afterChunk(chunk);
    listener.beforeStep(mock(StepExecution.class));
    listener.afterChunk(chunk);

    assertThat(output.getOut())
        .containsSubsequence("Processed 4 items.", "Processed 2 items.")
        .doesNotContain("Processed 6 items.");
  }

  @Test
  void stepExecutedTwice_countsFromZeroEachTime(CapturedOutput output) throws Exception {
    var jobRepository = new ResourcelessJobRepository();
    Step step =
        new StepBuilder("countingStep", jobRepository)
            .<String, String>chunk(2)
            .transactionManager(new ResourcelessTransactionManager())
            .reader(new RestartingListReader(List.of("a", "b", "c", "d")))
            .writer(items -> {})
            .listener(new PrintProcessedItemsChunkListener<>())
            .build();

    var first = execute(step, jobRepository);
    var firstRunOutput = output.getOut();
    var second = execute(step, jobRepository);
    var secondRunOutput = output.getOut().substring(firstRunOutput.length());

    assertThat(first.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(firstRunOutput).containsSubsequence("Processed 2 items.", "Processed 4 items.");
    assertThat(secondRunOutput)
        .containsSubsequence("Processed 2 items.", "Processed 4 items.")
        .doesNotContain("Processed 6 items.");
  }

  private static StepExecution execute(Step step, ResourcelessJobRepository jobRepository)
      throws Exception {
    var jobParameters = new JobParameters();
    var jobInstance = jobRepository.createJobInstance("countingJob", jobParameters);
    var jobExecution =
        jobRepository.createJobExecution(jobInstance, jobParameters, new ExecutionContext());
    var stepExecution = jobRepository.createStepExecution(step.getName(), jobExecution);
    step.execute(stepExecution);
    return stepExecution;
  }

  /** Reader that serves the same items again every time the step opens it. */
  private static final class RestartingListReader implements ItemStreamReader<String> {

    private final List<String> items;
    private int index;

    private RestartingListReader(List<String> items) {
      this.items = items;
    }

    @Override
    public void open(ExecutionContext executionContext) {
      index = 0;
    }

    @Override
    public String read() {
      return index < items.size() ? items.get(index++) : null;
    }
  }
}
