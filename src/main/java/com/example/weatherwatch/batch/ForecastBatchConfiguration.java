package com.example.weatherwatch.batch;

import com.example.weatherwatch.forecast.ForecastCache;
import com.example.weatherwatch.forecast.ForecastCacheKeys;
import com.example.weatherwatch.forecast.ForecastResponse;
import com.example.weatherwatch.forecast.ForecastProviderException;
import com.example.weatherwatch.forecast.ForecastSnapshot;
import com.example.weatherwatch.forecast.ForecastSnapshotRepository;
import com.example.weatherwatch.forecast.OpenMeteoClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;

@Configuration
public class ForecastBatchConfiguration {

    @Bean
    Job forecastRefreshJob(JobRepository jobRepository, Step forecastRefreshManagerStep) {
        return new JobBuilder("forecastRefreshJob", jobRepository)
                .start(forecastRefreshManagerStep)
                .build();
    }

    @Bean
    Step forecastRefreshManagerStep(
            JobRepository jobRepository,
            Step forecastRefreshWorkerStep,
            TaskExecutorPartitionHandler forecastPartitionHandler,
            LocationRangePartitioner locationRangePartitioner
    ) {
        return new StepBuilder("forecastRefreshManagerStep", jobRepository)
                .partitioner(forecastRefreshWorkerStep.getName(), locationRangePartitioner)
                .partitionHandler(forecastPartitionHandler)
                .build();
    }

    @Bean
    TaskExecutorPartitionHandler forecastPartitionHandler(
            Step forecastRefreshWorkerStep,
            TaskExecutor forecastPartitionTaskExecutor,
            @Value("${weather.batch.grid-size:4}") int gridSize
    ) throws Exception {
        if (gridSize < 1) {
            throw new IllegalArgumentException("Batch grid size must be positive");
        }
        TaskExecutorPartitionHandler handler = new TaskExecutorPartitionHandler();
        handler.setStep(forecastRefreshWorkerStep);
        handler.setTaskExecutor(forecastPartitionTaskExecutor);
        handler.setGridSize(gridSize);
        handler.afterPropertiesSet();
        return handler;
    }

    @Bean
    TaskExecutor forecastPartitionTaskExecutor(
            @Value("${weather.batch.max-workers:4}") int maxWorkers
    ) {
        if (maxWorkers < 1) {
            throw new IllegalArgumentException("Batch worker count must be positive");
        }
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("forecast-partition-");
        executor.setConcurrencyLimit(maxWorkers);
        return executor;
    }

    @Bean
    LocationRangePartitioner locationRangePartitioner(JdbcTemplate jdbcTemplate) {
        return new LocationRangePartitioner(jdbcTemplate);
    }

    @Bean
    Step forecastRefreshWorkerStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemReader<LocationBatchItem> locationBatchReader,
            ItemProcessor<LocationBatchItem, ForecastBatchItem> forecastBatchProcessor,
            ItemWriter<ForecastBatchItem> forecastBatchWriter,
            @Value("${weather.batch.chunk-size:10}") int chunkSize,
            @Value("${weather.batch.retry-limit:3}") int retryLimit
    ) {
        if (chunkSize < 1) {
            throw new IllegalArgumentException("Batch chunk size must be positive");
        }
        if (retryLimit < 1) {
            throw new IllegalArgumentException("Batch retry limit must be positive");
        }
        return new StepBuilder("forecastRefreshWorkerStep", jobRepository)
                .<LocationBatchItem, ForecastBatchItem>chunk(chunkSize, transactionManager)
                .reader(locationBatchReader)
                .processor(forecastBatchProcessor)
                .writer(forecastBatchWriter)
                .faultTolerant()
                .retry(ForecastProviderException.class)
                .retryLimit(retryLimit)
                .build();
    }

    @Bean
    @StepScope
    JdbcCursorItemReader<LocationBatchItem> locationBatchReader(
            DataSource dataSource,
            RowMapper<LocationBatchItem> locationBatchRowMapper,
            @Value("#{stepExecutionContext['minId']}") Long minimumId,
            @Value("#{stepExecutionContext['maxId']}") Long maximumId
    ) {
        return new JdbcCursorItemReaderBuilder<LocationBatchItem>()
                .name("locationBatchReader")
                .dataSource(dataSource)
                .sql("""
                        SELECT id, name, latitude, longitude
                        FROM locations
                        WHERE id >= ? AND id <= ?
                        ORDER BY id
                        """)
                .preparedStatementSetter(statement -> {
                    statement.setLong(1, minimumId);
                    statement.setLong(2, maximumId);
                })
                .rowMapper(locationBatchRowMapper)
                .saveState(true)
                .build();
    }

    @Bean
    RowMapper<LocationBatchItem> locationBatchRowMapper() {
        return (resultSet, rowNumber) -> new LocationBatchItem(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getDouble("latitude"),
                resultSet.getDouble("longitude")
        );
    }

    @Bean
    ItemProcessor<LocationBatchItem, ForecastBatchItem> forecastBatchProcessor(
            OpenMeteoClient openMeteoClient,
            ObjectMapper objectMapper
    ) {
        return location -> {
            ForecastResponse forecast = openMeteoClient.getCurrentForecast(
                    location.latitude(), location.longitude());
            String payload;
            try {
                payload = objectMapper.writeValueAsString(forecast);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Could not serialize forecast snapshot", exception);
            }
            ForecastSnapshot snapshot = new ForecastSnapshot(
                    location.id(),
                    forecast.current().time(),
                    Instant.now(),
                    payload
            );
            return new ForecastBatchItem(snapshot, forecast);
        };
    }

    @Bean
    ItemWriter<ForecastBatchItem> forecastBatchWriter(
            ForecastSnapshotRepository snapshotRepository,
            ForecastCache forecastCache
    ) {
        return chunk -> {
            List<? extends ForecastBatchItem> items = chunk.getItems();
            for (ForecastBatchItem item : items) {
                ForecastSnapshot incoming = item.snapshot();
                ForecastSnapshot existing = snapshotRepository
                        .findByLocationIdAndForecastTime(incoming.getLocationId(), incoming.getForecastTime())
                        .orElse(null);
                if (existing != null) {
                    existing.replaceForecast(incoming.getFetchedAt(), incoming.getPayload());
                } else {
                    snapshotRepository.save(incoming);
                }
            }
            for (ForecastBatchItem item : items) {
                forecastCache.put(ForecastCacheKeys.forLocation(item.snapshot().getLocationId()), item.forecast());
            }
        };
    }
}
