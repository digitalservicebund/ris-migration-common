package de.bund.digitalservice.ris.migration.common.model;

import org.w3c.dom.Document;

/**
 * One source file handed from the reader to the transformation step.
 *
 * @param filename absolute path of the source file, used to attribute errors back to their input
 * @param content raw file content as read from disk
 * @param document parsed DOM tree for XML input, {@code null} for formats parsed downstream (e.g.
 *     JSON)
 */
public record JurisDocument(String filename, String content, Document document) {}
