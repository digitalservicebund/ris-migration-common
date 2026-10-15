package de.bund.digitalservice.ris.migration.common.service;

/**
 * Key prefix in the publication bucket that every destination operation is scoped under, e.g.
 * {@code v1} to namespace a schema version within a shared bucket. Leading and trailing slashes are
 * stripped, so {@code v1}, {@code v1/} and {@code /v1/} are the same prefix; an empty value means
 * the bucket root.
 *
 * @param value canonical prefix without leading or trailing slash; empty for the bucket root
 */
public record DestinationPrefix(String value) {

  /**
   * Canonicalizes the prefix.
   *
   * @throws IllegalArgumentException if the prefix has an empty, {@code .} or {@code ..} segment,
   *     since those would produce keys that upload, delete and reconciliation disagree on
   */
  public DestinationPrefix {
    String raw = value;
    value = value.replaceAll("(?:^/+)|(?:/+$)", "");
    if (!value.isEmpty()) {
      for (String segment : value.split("/", -1)) {
        if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
          throw new IllegalArgumentException("Invalid destination prefix: '" + raw + "'");
        }
      }
    }
  }

  /**
   * Scopes a key relative to this prefix.
   *
   * @param relativeKey key below the prefix, e.g. {@code doc.xml}
   * @return the full bucket key, e.g. {@code v1/doc.xml}
   */
  public String key(String relativeKey) {
    return value.isEmpty() ? relativeKey : value + "/" + relativeKey;
  }

  /**
   * Prefix to list objects with so only keys inside this prefix's folder match, not siblings that
   * merely share its leading characters (e.g. {@code v10/}).
   *
   * @return {@code value + "/"}, or empty for the bucket root
   */
  public String listingPrefix() {
    return key("");
  }
}
