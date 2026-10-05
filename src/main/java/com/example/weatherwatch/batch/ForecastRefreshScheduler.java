package com.example.weatherwatch.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(name = "weather.batch.enabled", havingValue = "true", matchIfMissing = true)
public class ForecastRefreshScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ForecastRefreshScheduler.class);

    private final JobLauncher jobLauncher;
    private final Job forecastRefreshJob;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();

    public ForecastRefreshScheduler(JobLauncher jobLauncher, Job forecastRefreshJob, Clock clock) {
        this.jobLauncher = jobLauncher;
        this.forecastRefreshJob = forecastRefreshJob;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${weather.batch.fixed-delay:PT1H}")
    public void launchScheduledJob() {
        if (!running.compareAndSet(false, true)) {
            LOGGER.warn("Skipping scheduled forecast refresh because a previous run is still active");
            return;
        }
        try {
            JobParameters parameters = new JobParametersBuilder()
                    .addLong("scheduledAt", clock.millis())
                    .toJobParameters();
            JobExecution execution = jobLauncher.run(forecastRefreshJob, parameters);
            if (execution.getStatus().isUnsuccessful()) {
                LOGGER.error("Scheduled forecast refresh failed with status {} and execution id {}",
                        execution.getStatus(), execution.getId());
            } else {
                LOGGER.info("Scheduled forecast refresh completed with status {} and execution id {}",
                        execution.getStatus(), execution.getId());
            }
        } catch (Exception exception) {
            LOGGER.error("Could not launch scheduled forecast refresh", exception);
        } finally {
            running.set(false);
        }
    }
}
