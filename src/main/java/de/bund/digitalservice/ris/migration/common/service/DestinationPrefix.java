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

  private static final String CHANGELOG_FOLDER = "changelogs/";

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
   * merely share its leading characters (e.g. {@code v10/}). Same as {@code key("")}, which reuses
   * its bucket-root handling: a plain {@code value + "/"} would yield {@code "/"} for the root,
   * which matches no keys at all.
   *
   * @return {@code value + "/"}, or empty for the bucket root
   */
  public String listingPrefix() {
    return key("");
  }

  /**
   * Folder below this prefix that changelogs are published to, and that reconciliation skips.
   *
   * @return e.g. {@code v1/changelogs/}, or {@code changelogs/} for the bucket root
   */
  public String changelogPrefix() {
    return key(CHANGELOG_FOLDER);
  }

  /**
   * Full bucket key of a changelog file.
   *
   * @param fileName changelog file name, e.g. {@code 2026-01-01T00:00:00Z.json}
   * @return e.g. {@code v1/changelogs/2026-01-01T00:00:00Z.json}
   */
  public String changelogKey(String fileName) {
    return changelogPrefix() + fileName;
  }
}
