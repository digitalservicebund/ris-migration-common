package de.bund.digitalservice.ris.migration.common.repository;

import de.bund.digitalservice.ris.migration.common.model.CountedError;
import java.util.List;

public interface MigrationErrorRepository {

  /** Implementations must return results ordered by count descending. */
  List<CountedError> countAllGroupByDescription();
}
