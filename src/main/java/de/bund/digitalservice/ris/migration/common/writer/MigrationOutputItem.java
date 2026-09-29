package de.bund.digitalservice.ris.migration.common.writer;

/**
 * A migrated document ready to be written out. Projects implement this on whatever type their
 * transformation produces, which is what lets the writer stay independent of project-specific
 * models.
 */
public interface MigrationOutputItem {
  /**
   * Names the output file.
   *
   * @return document number, used verbatim as the file name stem and therefore as the published S3
   *     key
   */
  String getDocumentNumber();

  /**
   * Supplies the file's contents.
   *
   * @return the migrated document as it should be published
   */
  String getXmlContent();
}
