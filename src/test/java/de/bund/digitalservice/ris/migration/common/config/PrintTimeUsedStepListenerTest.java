package de.bund.digitalservice.ris.migration.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.step.StepExecution;

class PrintTimeUsedStepListenerTest {

	@Test
	void afterStep_returnsNull() {
		var listener = new PrintTimeUsedStepListener();
		var stepExecution = mock(StepExecution.class);
		when(stepExecution.getCreateTime()).thenReturn(LocalDateTime.of(2025, 1, 15, 10, 0, 0));
		when(stepExecution.getEndTime()).thenReturn(LocalDateTime.of(2025, 1, 15, 10, 5, 30));

		var result = listener.afterStep(stepExecution);

		assertThat(result).isNull();
	}

	@Test
	void afterStep_zeroDuration_doesNotThrow() {
		var listener = new PrintTimeUsedStepListener();
		var stepExecution = mock(StepExecution.class);
		var now = LocalDateTime.of(2025, 1, 15, 10, 0, 0);
		when(stepExecution.getCreateTime()).thenReturn(now);
		when(stepExecution.getEndTime()).thenReturn(now);

		listener.afterStep(stepExecution);
	}
}
