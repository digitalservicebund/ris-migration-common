package de.bund.digitalservice.ris.migration.common.config;

import jakarta.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.ChunkListener;
import org.springframework.batch.infrastructure.item.Chunk;

/**
 * Logs a running total of processed items after each chunk, so a long migration shows progress
 * rather than going silent until it finishes.
 *
 * @param <I> item type read into the step
 * @param <O> item type written out of the step
 */
@Slf4j
public class PrintProcessedItemsChunkListener<I, O> implements ChunkListener<I, O> {

  private final AtomicLong totalProcessed = new AtomicLong();

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
