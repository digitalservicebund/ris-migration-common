package de.bund.digitalservice.ris.migration.common.reader;

import de.bund.digitalservice.ris.migration.common.model.JurisDocument;
import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ParseException;
import org.springframework.batch.infrastructure.item.file.ResourceAwareItemReaderItemStream;
import org.springframework.core.io.Resource;
import org.xml.sax.SAXException;

/**
 * Reader delegate that creates a {@link JurisDocument} from a resource. For XML files a DOM {@link
 * org.w3c.dom.Document} is parsed; for JSON files the document field is {@code null}.
 */
@Slf4j
public class XmlDocumentItemReader implements ResourceAwareItemReaderItemStream<JurisDocument> {

  private final DocumentBuilder documentBuilder;
  private Resource currentResource;
  private boolean resourceRead = false;

  public XmlDocumentItemReader() {
    try {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
      dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      dbf.setXIncludeAware(false);
      dbf.setExpandEntityReferences(false);
      documentBuilder = dbf.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException("Could not create document builder.", e);
    }
  }

  @Override
  public void setResource(@Nonnull Resource resource) {
    this.currentResource = resource;
    this.resourceRead = false;
  }

  @Override
  public JurisDocument read() {
    if (currentResource == null || resourceRead) {
      return null;
    }
    try {
      String filename = currentResource.getFile().getCanonicalPath();
      String content = currentResource.getContentAsString(StandardCharsets.UTF_8);
      resourceRead = true;
      log.debug("Reading file: {}", currentResource.getFilename());

      String name = currentResource.getFilename();
      if (name != null && name.endsWith(".json")) {
        return new JurisDocument(filename, content, null);
      }

      var document = documentBuilder.parse(currentResource.getInputStream());
      document.getDocumentElement().normalize();
      return new JurisDocument(filename, content, document);
    } catch (IOException | SAXException e) {
      throw new ParseException("Could not parse file: " + currentResource.getFilename(), e);
    }
  }
}
