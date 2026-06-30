package de.bund.digitalservice.ris.migration.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.bund.digitalservice.ris.migration.common.domain.IncrementalMigrationStatus;
import de.bund.digitalservice.ris.migration.common.repository.IncrementalMigrationStatusRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.ExecutionContext;

@ExtendWith(MockitoExtension.class)
class MigrationStatusServiceTest {

	@Mock
	private IncrementalMigrationStatusRepository statusRepository;

	private MigrationStatusService service;

	@BeforeEach
	void setUp() {
		service = new MigrationStatusService(statusRepository);
	}

	@Test
	void updateStatus_daily_savesNewRecordWithDailyVersion() {
		when(statusRepository.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional
				.of(IncrementalMigrationStatus.builder().lastDailyImportVersion(LocalDate.of(2025, 1, 1)).build()));
		when(statusRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		var context = new ExecutionContext();
		context.put("newDailyVersion", LocalDate.of(2025, 1, 15));

		service.updateStatus(context, "daily");

		var captor = ArgumentCaptor.forClass(IncrementalMigrationStatus.class);
		verify(statusRepository).save(captor.capture());
		var saved = captor.getValue();
		assertThat(saved.getLastDailyImportVersion()).isEqualTo(LocalDate.of(2025, 1, 15));
		assertThat(saved.getId()).isNull();
		assertThat(saved.getCreatedAt()).isNull();
	}

	@Test
	void updateStatus_monthly_savesNewRecordWithHistoricAndDailyVersion() {
		when(statusRepository.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.empty());
		when(statusRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		var context = new ExecutionContext();
		context.put("newHistoricVersion", LocalDate.of(2024, 12, 26));

		service.updateStatus(context, "monthly");

		var captor = ArgumentCaptor.forClass(IncrementalMigrationStatus.class);
		verify(statusRepository).save(captor.capture());
		var saved = captor.getValue();
		assertThat(saved.getLastHistoricImportVersion()).isEqualTo(LocalDate.of(2024, 12, 26));
		assertThat(saved.getLastDailyImportVersion()).isEqualTo(LocalDate.of(2024, 12, 26));
	}

	@Test
	void updateStatus_noContextKeys_stillSaves() {
		when(statusRepository.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.empty());
		when(statusRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.updateStatus(new ExecutionContext(), "daily");

		verify(statusRepository).save(any());
	}

	@Test
	void updateStatus_existingStatusReused_preservesFields() {
		var existing = IncrementalMigrationStatus.builder().lastDailyImportVersion(LocalDate.of(2025, 1, 1))
				.lastHistoricImportVersion(LocalDate.of(2024, 12, 1)).build();
		when(statusRepository.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.of(existing));
		when(statusRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		var context = new ExecutionContext();
		context.put("newDailyVersion", LocalDate.of(2025, 1, 10));

		service.updateStatus(context, "daily");

		var captor = ArgumentCaptor.forClass(IncrementalMigrationStatus.class);
		verify(statusRepository).save(captor.capture());
		var saved = captor.getValue();
		assertThat(saved.getLastHistoricImportVersion()).isEqualTo(LocalDate.of(2024, 12, 1));
		assertThat(saved.getLastDailyImportVersion()).isEqualTo(LocalDate.of(2025, 1, 10));
	}
}
