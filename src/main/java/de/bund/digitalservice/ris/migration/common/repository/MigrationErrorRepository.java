package de.bund.digitalservice.ris.migration.common.repository;

import de.bund.digitalservice.ris.migration.common.model.CountedError;
import java.util.List;

public interface MigrationErrorRepository {
  List<CountedError> countAllGroupByDescription();
}
