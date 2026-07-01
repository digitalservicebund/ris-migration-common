package de.bund.digitalservice.ris.migration.common.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.bund.digitalservice.ris.migration.common.model.JurisXml;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ParseException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

class XmlDocumentItemReaderTest {

  private XmlDocumentItemReader reader;

  @BeforeEach
  void setUp() {
    reader = new XmlDocumentItemReader();
  }

  @Test
  void read_xmlFile_returnsParsedDocument() {
    reader.setResource(new ClassPathResource("test-document.xml"));

    JurisXml result = reader.read();

    assertThat(result).isNotNull();
    assertThat(result.document()).isNotNull();
    assertThat(result.content()).contains("<root>");
    assertThat(result.filename()).endsWith("test-document.xml");
  }

  @Test
  void read_jsonFile_returnsNullDocument() {
    reader.setResource(new ClassPathResource("test-document.json"));

    JurisXml result = reader.read();

    assertThat(result).isNotNull();
    assertThat(result.document()).isNull();
    assertThat(result.content()).contains("\"test\"");
  }

  @Test
  void read_calledTwiceOnSameResource_secondCallReturnsNull() {
    reader.setResource(new ClassPathResource("test-document.xml"));

    reader.read();
    JurisXml second = reader.read();

    assertThat(second).isNull();
  }

  @Test
  void read_noResourceSet_returnsNull() {
    JurisXml result = reader.read();
    assertThat(result).isNull();
  }

  @Test
  void read_invalidXml_throwsParseException() {
    var badXml =
        new ByteArrayResource("<unclosed>".getBytes()) {
          @Override
          public String getFilename() {
            return "bad.xml";
          }
        };
    reader.setResource(badXml);

    assertThatThrownBy(reader::read).isInstanceOf(ParseException.class);
  }

  @Test
  void setResource_resetsReadState() {
    reader.setResource(new ClassPathResource("test-document.xml"));
    reader.read();

    reader.setResource(new ClassPathResource("test-document.xml"));
    JurisXml result = reader.read();

    assertThat(result).isNotNull();
  }
}
