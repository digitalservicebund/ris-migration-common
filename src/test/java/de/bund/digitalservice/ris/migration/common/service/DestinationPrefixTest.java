package de.bund.digitalservice.ris.migration.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DestinationPrefixTest {

  @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
  @CsvSource(
      textBlock =
          """
          v1,        v1
          v1/,       v1
          /v1,       v1
          /v1/,      v1
          v1//,      v1
          //v1,      v1
          prod/v1,   prod/v1
          /prod/v1/, prod/v1
          '',        ''
          /,         ''
          ///,       ''
          """)
  void value_stripsLeadingAndTrailingSlashes(String raw, String expected) {
    assertThat(new DestinationPrefix(raw).value()).isEqualTo(expected);
  }

  @ParameterizedTest(name = "\"{0}\" + \"{1}\" -> \"{2}\"")
  @CsvSource(
      textBlock =
          """
          v1,      doc.xml,     v1/doc.xml
          /v1/,    doc.xml,     v1/doc.xml
          v1,      sub/doc.xml, v1/sub/doc.xml
          prod/v1, doc.xml,     prod/v1/doc.xml
          '',      doc.xml,     doc.xml
          /,       doc.xml,     doc.xml
          """)
  void key_scopesRelativeKeyBelowPrefix(String raw, String relativeKey, String expected) {
    assertThat(new DestinationPrefix(raw).key(relativeKey)).isEqualTo(expected);
  }

  @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
  @CsvSource(
      textBlock =
          """
          v1,      v1/
          /v1/,    v1/
          prod/v1, prod/v1/
          '',      ''
          /,       ''
          """)
  void listingPrefix_endsWithSlashOrIsEmptyForRoot(String raw, String expected) {
    assertThat(new DestinationPrefix(raw).listingPrefix()).isEqualTo(expected);
  }

  @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
  @CsvSource(
      textBlock =
          """
          v1,      v1/changelogs/
          prod/v1, prod/v1/changelogs/
          '',      changelogs/
          """)
  void changelogPrefix_isChangelogFolderBelowPrefix(String raw, String expected) {
    assertThat(new DestinationPrefix(raw).changelogPrefix()).isEqualTo(expected);
  }

  @ParameterizedTest(name = "\"{0}\" + \"{1}\" -> \"{2}\"")
  @CsvSource(
      textBlock =
          """
          v1, 2026-01-01T00:00:00Z.json, v1/changelogs/2026-01-01T00:00:00Z.json
          '', 2026-01-01T00:00:00Z.json, changelogs/2026-01-01T00:00:00Z.json
          """)
  void changelogKey_placesFileInChangelogFolder(String raw, String fileName, String expected) {
    assertThat(new DestinationPrefix(raw).changelogKey(fileName)).isEqualTo(expected);
  }

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(strings = {"prod//v1", "../v1", "v1/./x", "v1/..", "."})
  void constructor_rejectsMalformedPrefix(String raw) {
    assertThatThrownBy(() -> new DestinationPrefix(raw))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid destination prefix");
  }
}
