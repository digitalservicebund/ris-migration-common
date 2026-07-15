package de.bund.digitalservice.ris.migration.common.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModelTest {

  @Test
  void documentNumberReference_recordAccessor() {
    var ref = DocumentNumberReference.of("DOC001");
    assertThat(ref.documentNumber()).isEqualTo("DOC001");
  }

  @Test
  void jurisDocument_recordAccessors() {
    var doc = new JurisDocument("file.xml", "<root/>", null);
    assertThat(doc.filename()).isEqualTo("file.xml");
    assertThat(doc.content()).isEqualTo("<root/>");
    assertThat(doc.document()).isNull();
  }

  @Test
  void migrationStatus_values() {
    assertThat(MigrationStatus.values())
        .containsExactly(
            MigrationStatus.READ_SUCCEEDED,
            MigrationStatus.READ_FAILED,
            MigrationStatus.TRANSFORMATION_SUCCEEDED,
            MigrationStatus.TRANSFORMATION_FAILED,
            MigrationStatus.VALIDATION_FAILED);
  }

  @Test
  void migrationErrorType_values() {
    assertThat(MigrationErrorType.values())
        .containsExactly(MigrationErrorType.ERROR, MigrationErrorType.WARNING);
  }
}
