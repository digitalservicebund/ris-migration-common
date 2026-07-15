package de.bund.digitalservice.ris.migration.common.reader;

import static org.assertj.core.api.Assertions.assertThat;

import de.bund.digitalservice.ris.migration.common.model.DocumentNumberReference;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.core.io.ByteArrayResource;

class DeletionCsvItemReaderFactoryTest {

  @Test
  void create_readsDocumentNumberColumn_ignoringOthers() throws Exception {
    String csv =
        """
        documentUuid,documentNumber,deletionDate
        uuid-1,DOC001,2026-01-01
        uuid-2,DOC002,2026-01-02
        """;
    var resource = new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8));

    FlatFileItemReader<DocumentNumberReference> reader =
        DeletionCsvItemReaderFactory.create(
            "testReader",
            resource,
            ",",
            List.of("documentUuid", "documentNumber", "deletionDate"),
            "documentNumber");
    reader.open(new ExecutionContext());

    DocumentNumberReference first = reader.read();
    DocumentNumberReference second = reader.read();
    DocumentNumberReference third = reader.read();
    reader.close();

    assertThat(first.documentNumber()).isEqualTo("DOC001");
    assertThat(second.documentNumber()).isEqualTo("DOC002");
    assertThat(third).isNull();
  }

  @Test
  void create_missingResource_doesNotThrowOnOpen() throws Exception {
    var resource = new org.springframework.core.io.FileSystemResource("/nonexistent/deletions.csv");

    FlatFileItemReader<DocumentNumberReference> reader =
        DeletionCsvItemReaderFactory.create(
            "testReader",
            resource,
            ";",
            List.of("documentNumber", "documentType"),
            "documentNumber");
    reader.open(new ExecutionContext());

    DocumentNumberReference result = reader.read();
    reader.close();

    assertThat(result).isNull();
  }

  private record RichEntry(String documentNumber, String documentType, String deletedAt)
      implements DocumentNumberReference {}

  @Test
  void create_customMapper_preservesAllColumns() throws Exception {
    String csv =
        """
        documentNumber,documentType,deletedAt
        DOC001,ULI,2026-01-01
        """;
    var resource = new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8));

    FlatFileItemReader<RichEntry> reader =
        DeletionCsvItemReaderFactory.create(
            "testRichReader",
            resource,
            ",",
            List.of("documentNumber", "documentType", "deletedAt"),
            fieldSet ->
                new RichEntry(
                    fieldSet.readString("documentNumber"),
                    fieldSet.readString("documentType"),
                    fieldSet.readString("deletedAt")));
    reader.open(new ExecutionContext());

    RichEntry entry = reader.read();
    reader.close();

    assertThat(entry.documentNumber()).isEqualTo("DOC001");
    assertThat(entry.documentType()).isEqualTo("ULI");
    assertThat(entry.deletedAt()).isEqualTo("2026-01-01");
  }
}
