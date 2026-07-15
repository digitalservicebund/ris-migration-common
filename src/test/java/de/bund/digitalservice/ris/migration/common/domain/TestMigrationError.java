package de.bund.digitalservice.ris.migration.common.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/** Minimal concrete entity used only to verify {@link AbstractMigrationError} JPA mapping. */
@Entity
@Getter
@Setter
class TestMigrationError extends AbstractMigrationError {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "migration_record_id")
  private TestMigrationRecord migrationRecord;
}
