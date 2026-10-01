package de.bund.digitalservice.ris.migration.common.writer;

import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.annotations.NonNull;
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
   * @param outputDirectory directory the publishing step later uploads
   * @param fileExtension extension appended to each document number, e.g. {@code ".xml"}
   */
  public FileItemWriter(String outputDirectory, String fileExtension) {
    this(outputDirectory, fileExtension, _ -> true);
  }

  /**
   * Writes only the items a project-supplied filter accepts.
   *
   * @param outputDirectory directory the publishing step later uploads
   * @param fileExtension extension appended to each document number, e.g. {@code ".xml"}
   * @param writeFilter decides which items reach the output directory; items it rejects are
   *     silently skipped, so they are neither published nor recorded in the changelog
   */
  public FileItemWriter(String outputDirectory, String fileExtension, Predicate<T> writeFilter) {
    this.outputDirectory = outputDirectory;
    this.fileExtension = fileExtension;
    this.writeFilter = writeFilter;
  }

  /**
   * Creates the root output directory up front.
   *
   * @param executionContext execution context of the step (not used)
   * @throws ItemStreamException if the output directory cannot be created
   */
  @Override
  public void open(@Nonnull ExecutionContext executionContext) throws ItemStreamException {
    try {
      Files.createDirectories(Path.of(outputDirectory));
    } catch (IOException e) {
      throw new ItemStreamException(e);
    }
  }

  /**
   * Writes every item the filter accepts into a per-document subdirectory ({@code
   * docNum/docNum.ext}).
   *
   * @param chunk items to write
   */
  @Override
  public void write(@NonNull Chunk<? extends T> chunk) {
    write(chunk, MigrationOutputItem::getXmlContent);
  }

  /**
   * Writes every item the filter accepts into a per-document subdirectory ({@code
   * docNum/docNum.ext}).
   *
   * @param chunk items to write
   */
  public void write(Chunk<? extends T> chunk, Function<T, String> contentExtractor) {
    chunk.getItems().stream()
        .filter(writeFilter)
        .forEach(item -> writeToOutput(item, outputDirectory, fileExtension, contentExtractor));
  }

  /**
   * Writes one item into a per-document subdirectory ({@code docNum/docNum.ext}). Exposed for steps
   * that publish a document outside the chunk-oriented writer.
   *
   * @param item document to write
   * @param outputDirectory directory the publishing step later uploads
   * @param fileExtension extension appended to the document number
   * @param <T> output item type
   * @param contentExtractor Function being applied to the item to get the content
   * @throws IllegalArgumentException if the document number would place the file outside the output
   *     directory
   * @throws UncheckedIOException if the file cannot be written
   */
  public static <T extends MigrationOutputItem> void writeToOutput(
      T item, String outputDirectory, String fileExtension, Function<T, String> contentExtractor) {
    try {
      Path outputDir = Path.of(outputDirectory).toAbsolutePath().normalize();
      Path targetPath =
          outputDir
              .resolve(String.format("%1$s/%1$s%2$s", item.getDocumentNumber(), fileExtension))
              .normalize();
      if (!targetPath.startsWith(outputDir)) {
        throw new IllegalArgumentException("Invalid document number: " + item.getDocumentNumber());
      }
      Files.createDirectories(targetPath.getParent());
      Files.writeString(targetPath, contentExtractor.apply(item), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Writes the XML content of one item into a per-document subdirectory ({@code
   * docNum/docNum.ext}). Exposed for steps that publish a document outside the chunk-oriented
   * writer.
   *
   * @param item document to write
   * @param outputDirectory directory the publishing step later uploads
   * @param fileExtension extension appended to the document number
   * @param <T> output item type
   * @throws IllegalArgumentException if the document number would place the file outside the output
   *     directory
   * @throws UncheckedIOException if the file cannot be written
   */
  public static <T extends MigrationOutputItem> void writeToOutput(
      T item, String outputDirectory, String fileExtension) {
    writeToOutput(item, outputDirectory, fileExtension, MigrationOutputItem::getXmlContent);
  }
}
