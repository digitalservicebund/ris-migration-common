package de.bund.digitalservice.ris.migration.common.config;

import jakarta.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.ChunkListener;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;

/**
 * Logs a running total of processed items after each chunk, so a long migration shows progress
 * rather than going silent until it finishes.
 *
 * <p>The total is reset at the start of every step execution, so a step bean that runs several
 * times in one JVM (e.g. once per day in a monthly migration) starts counting at zero each time. Do
 * not share one instance between steps that run concurrently, as they would share one counter.
 *
 * <p>Registering it with {@code .listener(...)} on a chunk-oriented step ({@code
 * StepBuilder#chunk(int)}) wires both callbacks. On the legacy {@code SimpleStepBuilder} the call
 * is ambiguous; cast to {@code ChunkListener<I, O>} there.
 *
 * @param <I> item type read into the step
 * @param <O> item type written out of the step
 */
@Slf4j
public class PrintProcessedItemsChunkListener<I, O>
    implements ChunkListener<I, O>, StepExecutionListener {

  private final AtomicLong totalProcessed = new AtomicLong();

  /**
   * Resets the running total so every step execution starts counting at zero.
   *
   * @param stepExecution step execution that is about to start
   */
  @Override
  public void beforeStep(@Nonnull StepExecution stepExecution) {
    totalProcessed.set(0);
  }

  /**
   * Adds the chunk's size to the running total and logs it.
   *
   * @param chunk chunk that was just written
   */
  @Override
  public void afterChunk(@Nonnull Chunk<O> chunk) {
    long total = totalProcessed.addAndGet(chunk.size());
    log.info("Processed {} items.", total);
  }
}
