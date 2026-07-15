package de.bund.digitalservice.ris.migration.common.domain;

import de.bund.digitalservice.ris.migration.common.model.MigrationStatus;
import jakarta.persistence.Basic;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Base fields for the root migration result of one source document. Projects declare a concrete
 * {@code @Entity} extending this class, adding their own document/errors relations (e.g. a
 * {@code @OneToOne} to a project-specific document entity and a {@code @OneToMany} to a
 * project-specific migration-error entity).
 */
@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
public abstract class AbstractMigrationRecord {

  @Id @GeneratedValue private UUID id;

  @Enumerated(EnumType.STRING)
  @Basic(optional = false)
  private MigrationStatus migrationStatus;
}
