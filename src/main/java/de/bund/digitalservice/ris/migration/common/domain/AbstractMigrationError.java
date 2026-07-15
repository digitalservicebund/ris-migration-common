package de.bund.digitalservice.ris.migration.common.domain;

import de.bund.digitalservice.ris.migration.common.model.MigrationErrorType;
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
 * Base fields for a validation, reading, or transformation error collected against a migration
 * record. Projects declare a concrete {@code @Entity} extending this class, adding the
 * {@code @ManyToOne} back-reference to their own migration record entity.
 */
@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
public abstract class AbstractMigrationError {

  @Id @GeneratedValue private UUID id;

  @Basic(optional = false)
  @Enumerated(EnumType.STRING)
  private MigrationErrorType type;

  @Basic(optional = false)
  private String description;
}
