package de.bund.digitalservice.ris.migration.common.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModelTest {

  @Test
  void csvDeleteEntry_recordAccessors() {
    var entry = new CsvDeleteEntry("DOC001", "literature", "2025-01-15");
    assertThat(entry.documentNumber()).isEqualTo("DOC001");
    assertThat(entry.documentType()).isEqualTo("literature");
    assertThat(entry.deletedAt()).isEqualTo("2025-01-15");
  }

  @Test
  void documentNumberReference_recordAccessor() {
    var ref = new DocumentNumberReference("DOC001");
    assertThat(ref.documentNumber()).isEqualTo("DOC001");
  }

  @Test
  void jurisXml_recordAccessors() {
    var xml = new JurisXml("file.xml", "<root/>", null);
    assertThat(xml.filename()).isEqualTo("file.xml");
    assertThat(xml.content()).isEqualTo("<root/>");
    assertThat(xml.document()).isNull();
  }

  @Test
  void migrationStatus_values() {
    assertThat(MigrationStatus.TRANSFORMATION_SUCCEEDED).isNotNull();
  }
}
