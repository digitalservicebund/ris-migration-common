package de.bund.digitalservice.ris.migration.common.model;

/**
 * Identifies a document by number in a deletion feed. Projects whose deletion CSV carries only a
 * document number can use {@link #of(String)}; projects whose CSV carries extra columns can
 * implement this interface directly on their own record (see {@link
 * de.bund.digitalservice.ris.migration.common.reader.DeletionCsvItemReaderFactory}'s custom-mapper
 * overload) and still be consumed by {@link
 * de.bund.digitalservice.ris.migration.common.writer.S3DeletionWriter}.
 */
public interface DocumentNumberReference {

  String documentNumber();

  /**
   * Creates a minimal {@link DocumentNumberReference} carrying only a document number.
   *
   * @param documentNumber the document number
   * @return a document number reference wrapping the given value
   */
  static DocumentNumberReference of(String documentNumber) {
    return new Simple(documentNumber);
  }

  /** Minimal single-field implementation returned by {@link #of(String)}. */
  record Simple(String documentNumber) implements DocumentNumberReference {}
}
