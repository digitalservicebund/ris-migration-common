package de.bund.digitalservice.ris.migration.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.Chunk;

class PrintProcessedItemsChunkListenerTest {

  @Test
  void afterChunk_doesNotThrow() {
    var listener = new PrintProcessedItemsChunkListener<String, String>();
    assertThatCode(() -> listener.afterChunk(new Chunk<>(List.of("item1", "item2"))))
        .doesNotThrowAnyException();
  }

  @Test
  void afterChunk_multipleInvocations_incrementsCounter() {
    var listener = new PrintProcessedItemsChunkListener<String, String>();
    var chunk = new Chunk<>(List.of("a", "b"));
    assertThatCode(
            () -> {
              listener.afterChunk(chunk);
              listener.afterChunk(chunk);
            })
        .doesNotThrowAnyException();
  }

  @Test
  void afterChunk_emptyChunk_doesNotThrow() {
    var listener = new PrintProcessedItemsChunkListener<String, String>();
    assertThatCode(() -> listener.afterChunk(new Chunk<>(List.of()))).doesNotThrowAnyException();
  }
}
