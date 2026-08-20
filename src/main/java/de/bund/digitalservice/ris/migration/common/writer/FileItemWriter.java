package de.bund.digitalservice.ris.migration.common.writer;

import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamWriter;

/**
 * Writes migration output items as files to the configured output directory.
 *
 * @param <T> output item type carrying the document number and content to write
 */
public class FileItemWriter<T extends MigrationOutputItem> implements ItemStreamWriter<T> {

  private final String outputDirectory;
  private final String fileExtension;
  private final Predicate<T> writeFilter;

  /**
   * Writes every item it receives.
   *
   * @param outputDirectory directory the publish step later uploads
   * @param fileExtension extension appended to each document number, e.g. {@code ".akn.xml"}
   */
  public FileItemWriter(String outputDirectory, String fileExtension) {
    this(outputDirectory, fileExtension, _ -> true);
  }

  /**
   * Writes only the items a project-supplied filter accepts.
   *
   * @param outputDirectory directory the publish step later uploads
   * @param fileExtension extension appended to each document number, e.g. {@code ".akn.xml"}
   * @param writeFilter decides which items reach the output directory; items it rejects are
   *     silently skipped, so they are neither published nor recorded in the changelog
   */
  public FileItemWriter(String outputDirectory, String fileExtension, Predicate<T> writeFilter) {
    this.outputDirectory = outputDirectory;
    this.fileExtension = fileExtension;
    this.writeFilter = writeFilter;
  }

  @Override
  public void open(@Nonnull ExecutionContext executionContext) throws ItemStreamException {
    try {
      Files.createDirectories(Path.of(outputDirectory));
    } catch (IOException e) {
      throw new ItemStreamException(e);
    }
  }

  @Override
  public void write(Chunk<? extends T> chunk) {
    chunk.getItems().stream()
        .filter(writeFilter)
        .forEach(item -> writeToOutput(item, outputDirectory, fileExtension));
  }

  /**
   * Writes one item to the output directory, named after its document number. Exposed for steps
   * that publish a document outside the chunk-oriented writer.
   *
   * @param item document to write
   * @param outputDirectory directory the publish step later uploads
   * @param fileExtension extension appended to the document number
   * @param <T> output item type
   * @throws IllegalArgumentException if the document number would place the file outside the output
   *     directory
   * @throws UncheckedIOException if the file cannot be written
   */
  public static <T extends MigrationOutputItem> void writeToOutput(
      T item, String outputDirectory, String fileExtension) {
    try {
      Path outputDir = Path.of(outputDirectory).toAbsolutePath().normalize();
      Path targetPath = outputDir.resolve(item.getDocumentNumber() + fileExtension).normalize();
      if (!targetPath.startsWith(outputDir)) {
        throw new IllegalArgumentException("Invalid document number: " + item.getDocumentNumber());
      }
      Files.createDirectories(outputDir);
      Files.writeString(targetPath, item.getXmlContent(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
