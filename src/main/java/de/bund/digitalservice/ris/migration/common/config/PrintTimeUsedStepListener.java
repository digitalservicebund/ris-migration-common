package de.bund.digitalservice.ris.migration.common.config;

import jakarta.annotation.Nonnull;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;

/** Logs how long a step took, for comparing run durations across migrations. */
@Slf4j
public class PrintTimeUsedStepListener implements StepExecutionListener {

  private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

  @Override
  public void beforeStep(StepExecution stepExecution) {
    log.info(StringUtils.center(" STEP START ", 30, "#"));
    log.info(stepExecution.getStepName());
    log.info(StringUtils.repeat("#", 30));
  }

  /**
   * Logs the step's duration as {@code hh:mm:ss.SSS}.
   *
   * @param stepExecution execution of the step that just finished
   * @return {@code null} to leave the step's exit status unchanged
   */
  @Override
  public ExitStatus afterStep(@Nonnull StepExecution stepExecution) {
    Duration duration =
        Duration.between(
            stepExecution.getCreateTime().atZone(ZONE).toInstant(),
            stepExecution.getEndTime().atZone(ZONE).toInstant());
    DecimalFormat df = new DecimalFormat("00");
    log.info(StringUtils.center(" STEP END ", 30, "#"));
    log.info("##### STEP END {}", StringUtils.repeat("#", 14));
    log.info(
        "Time needed for {}: {}:{}:{}.{}.",
        stepExecution.getStepName(),
        df.format(duration.toHoursPart()),
        df.format(duration.toMinutesPart()),
        df.format(duration.toSecondsPart()),
        duration.toMillisPart());
    log.info(StringUtils.repeat("#", 30));
    return null;
  }
}
