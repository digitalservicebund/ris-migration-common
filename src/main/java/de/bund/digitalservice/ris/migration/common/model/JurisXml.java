package de.bund.digitalservice.ris.migration.common.model;

import org.w3c.dom.Document;

public record JurisXml(String filename, String content, Document document) {
}
