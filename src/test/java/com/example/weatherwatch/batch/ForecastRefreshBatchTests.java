package com.example.weatherwatch.batch;

import com.example.weatherwatch.forecast.ForecastCache;
import com.example.weatherwatch.forecast.ForecastProviderException;
import com.example.weatherwatch.forecast.ForecastResponse;
import com.example.weatherwatch.forecast.ForecastSnapshotRepository;
import com.example.weatherwatch.forecast.OpenMeteoClient;
import com.example.weatherwatch.location.Location;
import com.example.weatherwatch.location.LocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:batch-weather;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.batch.job.enabled=false",
        "weather.batch.enabled=false",
        "weather.batch.grid-size=3",
        "weather.batch.max-workers=2",
        "weather.batch.chunk-size=2",
        "weather.kafka.create-topic=false",
        "spring.kafka.listener.auto-startup=false"
})
class ForecastRefreshBatchTests {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job forecastRefreshJob;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private ForecastSnapshotRepository snapshotRepository;

    @MockitoBean
    private OpenMeteoClient openMeteoClient;

    @MockitoBean
    private ForecastCache forecastCache;

    @BeforeEach
    void setUp() {
        snapshotRepository.deleteAll();
        locationRepository.deleteAll();
        for (int index = 0; index < 5; index++) {
            locationRepository.save(new Location("Location " + index, index, index + 10));
        }
        clearInvocations(openMeteoClient, forecastCache);
        when(openMeteoClient.getCurrentForecast(org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble())).thenReturn(sampleForecast());
    }

    @Test
    void partitionsLocationsFetchesForecastsPersistsSnapshotsAndWarmsCache() throws Exception {
        JobParameters parameters = new JobParametersBuilder()
                .addLong("testRun", 1L)
                .toJobParameters();

        JobExecution execution = jobLauncher.run(forecastRefreshJob, parameters);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getStepExecutions())
                .filteredOn(step -> step.getStepName().startsWith("forecastRefreshWorkerStep"))
                .hasSize(3)
                .allSatisfy(step -> assertThat(step.getStatus()).isEqualTo(BatchStatus.COMPLETED));
        assertThat(snapshotRepository.count()).isEqualTo(5);
        verify(openMeteoClient, times(5)).getCurrentForecast(
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble());
        verify(forecastCache, times(5)).put(anyString(), eq(sampleForecast()));
    }

    @Test
    void rerunningWithNewJobParametersUpdatesSnapshotsInsteadOfDuplicatingThem() throws Exception {
        JobParameters firstRun = new JobParametersBuilder().addLong("testRun", 2L).toJobParameters();
        JobParameters secondRun = new JobParametersBuilder().addLong("testRun", 3L).toJobParameters();

        assertThat(jobLauncher.run(forecastRefreshJob, firstRun).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jobLauncher.run(forecastRefreshJob, secondRun).getStatus()).isEqualTo(BatchStatus.COMPLETED);

        assertThat(snapshotRepository.count()).isEqualTo(5);
    }

    @Test
    void retriesTransientProviderFailureAndCompletesPartitionedJob() throws Exception {
        when(openMeteoClient.getCurrentForecast(org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble()))
                .thenThrow(new ForecastProviderException("temporary provider failure"))
                .thenReturn(sampleForecast());
        JobParameters parameters = new JobParametersBuilder().addLong("testRun", 4L).toJobParameters();

        JobExecution execution = jobLauncher.run(forecastRefreshJob, parameters);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(snapshotRepository.count()).isEqualTo(5);
        verify(openMeteoClient, times(6)).getCurrentForecast(
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void neverRunsMoreWorkerPartitionsThanConfiguredMaxWorkers() throws Exception {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        when(openMeteoClient.getCurrentForecast(org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble())).thenAnswer(invocation -> {
            maxActive.accumulateAndGet(active.incrementAndGet(), Math::max);
            try {
                Thread.sleep(150);
            } finally {
                active.decrementAndGet();
            }
            return sampleForecast();
        });
        JobParameters parameters = new JobParametersBuilder().addLong("testRun", 5L).toJobParameters();

        JobExecution execution = jobLauncher.run(forecastRefreshJob, parameters);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(maxActive.get()).isBetween(1, 2);
    }

    @Test
    void permanentFailureFailsJobAndSameParametersCanBeRestartedToCompletion() throws Exception {
        when(openMeteoClient.getCurrentForecast(org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble()))
                .thenThrow(new ForecastProviderException("provider down"));
        JobParameters parameters = new JobParametersBuilder().addLong("testRun", 6L).toJobParameters();

        JobExecution failed = jobLauncher.run(forecastRefreshJob, parameters);

        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(snapshotRepository.count()).isZero();

        org.mockito.Mockito.doReturn(sampleForecast()).when(openMeteoClient).getCurrentForecast(
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble());
        JobExecution restarted = jobLauncher.run(forecastRefreshJob, parameters);

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(snapshotRepository.count()).isEqualTo(5);
    }

    private static ForecastResponse sampleForecast() {
        return new ForecastResponse(
                0,
                0,
                0,
                0,
                "GMT",
                "GMT",
                0,
                new ForecastResponse.CurrentWeather(
                        "2026-10-04T00:00",
                        900,
                        20,
                        50,
                        20,
                        1,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        1013,
                        1013,
                        0,
                        0,
                        0
                ),
                new ForecastResponse.CurrentWeatherUnits(
                        "iso8601",
                        "seconds",
                        "°C",
                        "%",
                        "°C",
                        "",
                        "mm",
                        "mm",
                        "mm",
                        "cm",
                        "wmo code",
                        "%",
                        "hPa",
                        "hPa",
                        "km/h",
                        "°",
                        "km/h"
                )
        );
    }
}
