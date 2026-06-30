package de.bund.digitalservice.ris.migration.common.config;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.Chunk;

class PrintProcessedItemsChunkListenerTest {

	@Test
	void afterChunk_doesNotThrow() {
		var listener = new PrintProcessedItemsChunkListener<String, String>();
		listener.afterChunk(new Chunk<>(List.of("item1", "item2")));
	}

	@Test
	void afterChunk_multipleInvocations_incrementsCounter() {
		var listener = new PrintProcessedItemsChunkListener<String, String>();
		var chunk = new Chunk<>(List.of("a", "b"));

		listener.afterChunk(chunk);
		listener.afterChunk(chunk);
	}

	@Test
	void afterChunk_emptyChunk_doesNotThrow() {
		var listener = new PrintProcessedItemsChunkListener<String, String>();
		listener.afterChunk(new Chunk<>(List.of()));
	}
}
