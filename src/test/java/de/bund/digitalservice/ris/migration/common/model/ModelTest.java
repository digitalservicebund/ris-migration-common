package de.bund.digitalservice.ris.migration.common.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModelTest {

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
