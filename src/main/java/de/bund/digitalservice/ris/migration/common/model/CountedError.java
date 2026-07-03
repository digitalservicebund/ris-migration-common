package de.bund.digitalservice.ris.migration.common.model;

public interface CountedError {
  Long getCount();

  String getDescription();
}
