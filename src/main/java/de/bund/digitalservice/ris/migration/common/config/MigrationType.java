package de.bund.digitalservice.ris.migration.common.config;

/** Migration type enumeration defining possible migration jobs. */
public enum MigrationType {
  /** Regular daily delta migration. */
  DAILY,
  /** Manual monthly full migration. */
  MONTHLY,
  /** Citation synchronization */
  CITATION_SYNCHRONIZATION,
}
