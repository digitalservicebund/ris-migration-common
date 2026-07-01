package de.bund.digitalservice.ris.migration.common.model;

public interface CountedError {
  long getCount();

  String getDescription();
}
