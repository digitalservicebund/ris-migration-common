package de.bund.digitalservice.ris.migration.common.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;

/**
 * Builds S3 key prefixes for daily and monthly dumps. The {@code subPath} suffix is appended after
 * the date segment, allowing each project to target its own sub-folder (e.g. {@code "BZSt/"}).
 */
@RequiredArgsConstructor
public class BucketPrefixBuilder {

  private static final String DAILY_PREFIX = "daily/";
  private static final String MONTHLY_PREFIX = "monthly/";

  private final String subPath;

  /**
   * Locates a month's full dump.
   *
   * @param yearMonth month whose full dump is being located
   * @return prefix of the monthly dump, e.g. {@code monthly/2026/04/BZSt/}
   */
  public String buildMonthlyPrefix(YearMonth yearMonth) {
    return MONTHLY_PREFIX + yearMonth.format(DateTimeFormatter.ofPattern("yyyy/MM/")) + subPath;
  }

  /**
   * Locates one day's export.
   *
   * @param day export day whose delta is being located
   * @return prefix of that day's export, e.g. {@code daily/2026/04/16/BZSt/}
   */
  public String buildDailyPrefix(LocalDate day) {
    return DAILY_PREFIX + day.format(DateTimeFormatter.ofPattern("yyyy/MM/dd/")) + subPath;
  }
}
