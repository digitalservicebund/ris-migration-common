package de.bund.digitalservice.ris.migration.common.writer;

public interface MigrationOutputItem {
  String getDocumentNumber();

  String getXmlContent();
}
