package de.bund.digitalservice.ris.migration.common.config;

import jakarta.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.ChunkListener;
import org.springframework.batch.infrastructure.item.Chunk;

@Slf4j
public class PrintProcessedItemsChunkListener<I, O> implements ChunkListener<I, O> {

	private final AtomicLong counter = new AtomicLong();

	@Override
	public void afterChunk(@Nonnull Chunk<O> chunk) {
		long page = counter.incrementAndGet();
		log.info("Processed {} items.", chunk.size() * page);
	}
}
