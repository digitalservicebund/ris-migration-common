package de.bund.digitalservice.ris.migration.common.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import de.bund.digitalservice.ris.migration.common.domain.IncrementalMigrationStatus;
import java.time.LocalDate;
import java.time.Month;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@DataJpaTest
@EntityScan(basePackageClasses = IncrementalMigrationStatus.class)
@EnableJpaRepositories(basePackageClasses = JpaIncrementalMigrationStatusRepository.class)
class JpaIncrementalMigrationStatusRepositoryTest {

  @Autowired private JpaIncrementalMigrationStatusRepository repository;

  @Test
  void savesAndFindsMostRecentStatus() {
    IncrementalMigrationStatus saved =
        repository.save(
            IncrementalMigrationStatus.builder()
                .lastDailyImportVersion(LocalDate.of(2026, Month.JANUARY, 2))
                .build());

    var found = repository.findFirstByOrderByCreatedAtDesc();

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(saved.getId());
    assertThat(found.get().getLastDailyImportVersion())
        .isEqualTo(LocalDate.of(2026, Month.JANUARY, 2));
  }
}
