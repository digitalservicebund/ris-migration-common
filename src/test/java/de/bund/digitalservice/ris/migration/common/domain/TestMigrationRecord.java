package de.bund.digitalservice.ris.migration.common.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Minimal concrete entity used only to verify {@link AbstractMigrationRecord} JPA mapping. */
@Entity
@Getter
@Setter
class TestMigrationRecord extends AbstractMigrationRecord {

  @OneToMany(
      fetch = FetchType.EAGER,
      mappedBy = "migrationRecord",
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  private List<TestMigrationError> migrationErrors = new ArrayList<>();
}
