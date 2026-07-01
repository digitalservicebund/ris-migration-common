package de.bund.digitalservice.ris.migration.common.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class BucketPrefixBuilderTest {

  @Test
  void buildDailyPrefix_noSubPath() {
    var builder = new BucketPrefixBuilder("");
    assertThat(builder.buildDailyPrefix(LocalDate.of(2025, Month.JANUARY, 15))).isEqualTo("daily/2025/01/15/");
  }

  @Test
  void buildDailyPrefix_withSubPath() {
    var builder = new BucketPrefixBuilder("BZSt/");
    assertThat(builder.buildDailyPrefix(LocalDate.of(2025, Month.JANUARY, 15)))
        .isEqualTo("daily/2025/01/15/BZSt/");
  }

  @Test
  void buildMonthlyPrefix_noSubPath() {
    var builder = new BucketPrefixBuilder("");
    assertThat(builder.buildMonthlyPrefix(YearMonth.of(2025, Month.MARCH))).isEqualTo("monthly/2025/03/");
  }

  @Test
  void buildMonthlyPrefix_withSubPath() {
    var builder = new BucketPrefixBuilder("BZSt/");
    assertThat(builder.buildMonthlyPrefix(YearMonth.of(2025, Month.MARCH)))
        .isEqualTo("monthly/2025/03/BZSt/");
  }
}
