package de.bund.digitalservice.ris.migration.common.reader;

import static org.assertj.core.api.Assertions.assertThat;

import de.bund.digitalservice.ris.migration.common.model.JurisXml;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.ExecutionContext;

class FileItemReaderTest {

  private XmlDocumentItemReader delegate;

  @BeforeEach
  void setUp() {
    delegate = new XmlDocumentItemReader();
  }

  @Test
  void read_xmlFiles_returnsItemsAndThenNull(@TempDir Path dir) throws Exception {
    Files.writeString(dir.resolve("doc1.xml"), "<root><element>one</element></root>");
    Files.writeString(dir.resolve("doc2.xml"), "<root><element>two</element></root>");

    var reader = new FileItemReader(dir.toString(), delegate, ".xml");
    reader.open(new ExecutionContext());

    JurisXml first = reader.read();
    JurisXml second = reader.read();
    JurisXml third = reader.read();

    reader.close();

    assertThat(first).isNotNull();
    assertThat(second).isNotNull();
    assertThat(third).isNull();
  }

  @Test
  void read_missingDirectory_returnsNull() throws Exception {
    var reader = new FileItemReader("/nonexistent/path", delegate, ".xml");
    var context = new ExecutionContext();
    reader.open(context);

    JurisXml result = reader.read();
    reader.close();

    assertThat(result).isNull();
    assertThat((boolean) context.get("NO_INPUT_DATA")).isTrue();
  }

  @Test
  void read_filterByExtension_skipsNonMatchingFiles(@TempDir Path dir) throws Exception {
    Files.writeString(dir.resolve("doc.xml"), "<root><element>x</element></root>");
    Files.writeString(dir.resolve("doc.json"), "{\"test\":\"value\"}");

    var reader = new FileItemReader(dir.toString(), delegate, ".xml");
    reader.open(new ExecutionContext());

    JurisXml item = reader.read();
    JurisXml noMore = reader.read();
    reader.close();

    assertThat(item).isNotNull();
    assertThat(item.document()).isNotNull();
    assertThat(noMore).isNull();
  }

  @Test
  void update_storesCurrentFileIndex(@TempDir Path dir) throws Exception {
    Files.writeString(dir.resolve("doc1.xml"), "<root><element>one</element></root>");

    var reader = new FileItemReader(dir.toString(), delegate, ".xml");
    var context = new ExecutionContext();
    reader.open(context);
    reader.read();
    reader.update(context);
    reader.close();

    assertThat(context.getLong("current.file.index")).isEqualTo(1L);
  }

  @Test
  void open_withRestartIndex_skipsProcessedFiles(@TempDir Path dir) throws Exception {
    Files.writeString(dir.resolve("a.xml"), "<root><element>a</element></root>");
    Files.writeString(dir.resolve("b.xml"), "<root><element>b</element></root>");

    var context = new ExecutionContext();
    context.put("current.file.index", 1L);

    var reader = new FileItemReader(dir.toString(), delegate, ".xml");
    reader.open(context);

    JurisXml item = reader.read();
    JurisXml none = reader.read();
    reader.close();

    assertThat(item).isNotNull();
    assertThat(none).isNull();
  }

  @Test
  void read_customPredicate_filtersCorrectly(@TempDir Path dir) throws Exception {
    Files.writeString(dir.resolve("include.xml"), "<root><element>keep</element></root>");
    Files.writeString(dir.resolve("exclude.xml"), "<root><element>drop</element></root>");

    var reader =
        new FileItemReader(
            dir.toString(), delegate, path -> path.getFileName().toString().startsWith("include"));
    reader.open(new ExecutionContext());

    JurisXml item = reader.read();
    JurisXml none = reader.read();
    reader.close();

    assertThat(item).isNotNull();
    assertThat(none).isNull();
  }
}
