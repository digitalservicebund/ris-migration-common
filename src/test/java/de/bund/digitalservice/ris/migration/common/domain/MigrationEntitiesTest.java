package de.bund.digitalservice.ris.migration.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import de.bund.digitalservice.ris.migration.common.model.MigrationErrorType;
import de.bund.digitalservice.ris.migration.common.model.MigrationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@DataJpaTest
@EntityScan(basePackageClasses = TestMigrationRecord.class)
class MigrationEntitiesTest {

  @Autowired private TestEntityManager entityManager;

  @Test
  void migrationRecord_persistsStatusAndCascadesErrors() {
    var migrationRecord = new TestMigrationRecord();
    migrationRecord.setMigrationStatus(MigrationStatus.TRANSFORMATION_SUCCEEDED);

    var error = new TestMigrationError();
    error.setType(MigrationErrorType.WARNING);
    error.setDescription("some warning");
    error.setMigrationRecord(migrationRecord);
    migrationRecord.getMigrationErrors().add(error);

    var saved = entityManager.persistFlushFind(migrationRecord);

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getMigrationStatus()).isEqualTo(MigrationStatus.TRANSFORMATION_SUCCEEDED);
    assertThat(saved.getMigrationErrors()).hasSize(1);
    assertThat(saved.getMigrationErrors().getFirst().getId()).isNotNull();
    assertThat(saved.getMigrationErrors().getFirst().getType())
        .isEqualTo(MigrationErrorType.WARNING);
    assertThat(saved.getMigrationErrors().getFirst().getDescription()).isEqualTo("some warning");
  }
}
