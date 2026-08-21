package de.bund.digitalservice.ris.migration.common.model;

/**
 * Stage a document reached in the migration pipeline. The stages are ordered: reading precedes
 * transformation, which precedes validation, so the recorded value also identifies where a document
 * dropped out.
 */
public enum MigrationStatus {
  /** The source file was read and parsed. */
  READ_SUCCEEDED,
  /** The source file could not be read or parsed, so nothing downstream ran for it. */
  READ_FAILED,
  /** Source content was converted into the target format. */
  TRANSFORMATION_SUCCEEDED,
  /** Conversion into the target format failed; no output was produced. */
  TRANSFORMATION_FAILED,
  /** Output was produced but rejected against the target schema, so it is not published. */
  VALIDATION_FAILED
}
