package de.bund.digitalservice.ris.migration.common.model;

public record CsvDeleteEntry(String documentNumber, String documentType, String deletedAt) {}
