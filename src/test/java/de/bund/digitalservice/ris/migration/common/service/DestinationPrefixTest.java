package de.bund.digitalservice.ris.migration.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DestinationPrefixTest {

  @ParameterizedTest
  @ValueSource(strings = {"v1", "v1/", "/v1", "/v1/", "v1//", "//v1"})
  void slashVariants_normalizeToSamePrefix(String raw) {
    DestinationPrefix prefix = new DestinationPrefix(raw);

    assertThat(prefix.value()).isEqualTo("v1");
    assertThat(prefix.key("doc.xml")).isEqualTo("v1/doc.xml");
    assertThat(prefix.listingPrefix()).isEqualTo("v1/");
  }

  @ParameterizedTest
  @ValueSource(strings = {"prod/v1", "/prod/v1/"})
  void nested_isKept(String raw) {
    DestinationPrefix prefix = new DestinationPrefix(raw);

    assertThat(prefix.key("doc.xml")).isEqualTo("prod/v1/doc.xml");
    assertThat(prefix.listingPrefix()).isEqualTo("prod/v1/");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "/", "///"})
  void emptyOrOnlySlashes_meansBucketRoot(String raw) {
    DestinationPrefix prefix = new DestinationPrefix(raw);

    assertThat(prefix.value()).isEmpty();
    assertThat(prefix.key("doc.xml")).isEqualTo("doc.xml");
    assertThat(prefix.listingPrefix()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"prod//v1", "../v1", "v1/./x", "v1/..", "."})
  void malformed_isRejected(String raw) {
    assertThatThrownBy(() -> new DestinationPrefix(raw))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid destination prefix");
  }
}
