package de.bund.digitalservice.ris.migration.common.model;

/** Severity of a problem recorded while migrating a document. */
public enum MigrationErrorType {
  /** The document could not be migrated and is missing from the published output. */
  ERROR,
  /** The document was migrated, but with a known loss of fidelity worth reviewing. */
  WARNING
}
