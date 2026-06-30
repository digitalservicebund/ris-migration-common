package de.bund.digitalservice.ris.migration.common.config;

import jakarta.annotation.Nonnull;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;

@Slf4j
public class PrintTimeUsedStepListener implements StepExecutionListener {

	private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

	@Override
	public ExitStatus afterStep(@Nonnull StepExecution stepExecution) {
		Duration duration = Duration.between(stepExecution.getCreateTime().atZone(ZONE).toInstant(),
				stepExecution.getEndTime().atZone(ZONE).toInstant());
		DecimalFormat df = new DecimalFormat("00");
		log.info("Time needed: {}:{}:{}.{}.", df.format(duration.toHoursPart()), df.format(duration.toMinutesPart()),
				df.format(duration.toSecondsPart()), duration.toMillisPart());
		return null;
	}
}
