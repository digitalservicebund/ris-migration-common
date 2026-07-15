package de.bund.digitalservice.ris.migration.common.reader;

import de.bund.digitalservice.ris.migration.common.model.DocumentNumberReference;
import java.util.List;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.mapping.FieldSetMapper;
import org.springframework.core.io.Resource;

/**
 * Builds a {@link FlatFileItemReader} for CSV deletion lists (a header row followed by one entry
 * per line). The resource is read non-strict, since a deletion CSV may not exist for every run.
 */
public final class DeletionCsvItemReaderFactory {

  private DeletionCsvItemReaderFactory() {}

  /**
   * Builds a reader producing a minimal {@link DocumentNumberReference} per row, discarding any
   * columns beyond {@code documentNumberColumn}. Use the {@link #create(String, Resource, String,
   * List, FieldSetMapper)} overload instead when other CSV columns need to be preserved.
   *
   * @param name reader bean name
   * @param resource CSV resource
   * @param delimiter column delimiter
   * @param columnNames all column names, in file order
   * @param documentNumberColumn name of the column holding the document number
   * @return a reader producing {@link DocumentNumberReference} items
   */
  public static FlatFileItemReader<DocumentNumberReference> create(
      String name,
      Resource resource,
      String delimiter,
      List<String> columnNames,
      String documentNumberColumn) {
    return create(
        name,
        resource,
        delimiter,
        columnNames,
        fieldSet -> DocumentNumberReference.of(fieldSet.readString(documentNumberColumn)));
  }

  /**
   * Builds a reader using a project-supplied row mapper, for deletion CSVs carrying columns beyond
   * a bare document number (e.g. a document type or deletion date). The mapped type must implement
   * {@link DocumentNumberReference} so it can still be consumed by {@link
   * de.bund.digitalservice.ris.migration.common.writer.S3DeletionWriter}.
   *
   * @param name reader bean name
   * @param resource CSV resource
   * @param delimiter column delimiter
   * @param columnNames all column names, in file order
   * @param fieldSetMapper maps one CSV row to the project's own {@link DocumentNumberReference}
   *     implementation
   * @return a reader producing items of the project's row type
   */
  public static <T extends DocumentNumberReference> FlatFileItemReader<T> create(
      String name,
      Resource resource,
      String delimiter,
      List<String> columnNames,
      FieldSetMapper<T> fieldSetMapper) {
    return new FlatFileItemReaderBuilder<T>()
        .name(name)
        .resource(resource)
        .linesToSkip(1)
        .strict(false)
        .delimited(
            delimited -> delimited.delimiter(delimiter).names(columnNames.toArray(new String[0])))
        .fieldSetMapper(fieldSetMapper)
        .build();
  }
}
