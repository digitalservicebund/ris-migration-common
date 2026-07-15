package de.bund.digitalservice.ris.migration.common.writer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.bund.digitalservice.ris.migration.common.model.DocumentNumberReference;
import de.bund.digitalservice.ris.migration.common.service.S3MigrationService;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.Chunk;

@ExtendWith(MockitoExtension.class)
class S3DeletionWriterTest {

  @Mock private S3MigrationService s3MigrationService;
  @Mock private Function<String, Optional<String>> recordFinder;
  @Mock private Consumer<String> recordDeleter;

  private S3DeletionWriter<String> writer;

  @BeforeEach
  void setUp() {
    writer = new S3DeletionWriter<>(s3MigrationService, recordFinder, recordDeleter, ".akn.xml");
  }

  @Test
  void write_deletesFromS3AndDatabase() {
    when(recordFinder.apply("DOC001")).thenReturn(Optional.of("entity-DOC001"));

    writer.write(new Chunk<>(List.of(DocumentNumberReference.of("DOC001"))));

    verify(s3MigrationService).delete("DOC001.akn.xml");
    verify(recordFinder).apply("DOC001");
    verify(recordDeleter).accept("entity-DOC001");
  }

  @Test
  void write_recordNotInDatabase_onlyDeletesFromS3() {
    when(recordFinder.apply("MISSING")).thenReturn(Optional.empty());

    writer.write(new Chunk<>(List.of(DocumentNumberReference.of("MISSING"))));

    verify(s3MigrationService).delete("MISSING.akn.xml");
    verify(recordDeleter, never()).accept(any());
  }

  @Test
  void write_multipleReferences_processesAll() {
    when(recordFinder.apply("DOC001")).thenReturn(Optional.of("entity1"));
    when(recordFinder.apply("DOC002")).thenReturn(Optional.of("entity2"));

    writer.write(
        new Chunk<>(
            List.of(DocumentNumberReference.of("DOC001"), DocumentNumberReference.of("DOC002"))));

    verify(s3MigrationService).delete("DOC001.akn.xml");
    verify(s3MigrationService).delete("DOC002.akn.xml");
    verify(recordDeleter).accept("entity1");
    verify(recordDeleter).accept("entity2");
  }

  @Test
  void write_emptyChunk_doesNothing() {
    writer.write(new Chunk<>(List.of()));

    verify(s3MigrationService, never()).delete(any());
  }

  @Test
  void write_localMode_skipsS3DeleteButStillDeletesRecord() {
    S3DeletionWriter<String> localWriter =
        new S3DeletionWriter<>(null, recordFinder, recordDeleter, ".akn.xml");
    when(recordFinder.apply("DOC001")).thenReturn(Optional.of("entity-DOC001"));

    localWriter.write(new Chunk<>(List.of(DocumentNumberReference.of("DOC001"))));

    verify(recordDeleter).accept("entity-DOC001");
  }
}
