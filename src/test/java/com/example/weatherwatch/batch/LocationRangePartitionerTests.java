package com.example.weatherwatch.batch;

import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocationRangePartitionerTests {

    @Test
    void createsDisjointContiguousRangesAcrossTheLocationIdSpan() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT COALESCE(MIN(id), 1) FROM locations", Long.class))
                .thenReturn(10L);
        when(jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM locations", Long.class))
                .thenReturn(18L);

        Map<String, ExecutionContext> partitions = new LocationRangePartitioner(jdbcTemplate).partition(4);

        List<String> names = partitions.keySet().stream().toList();
        assertThat(names).containsExactly(
                "location-partition-0",
                "location-partition-1",
                "location-partition-2",
                "location-partition-3");
        assertThat(partitions.values()).extracting(context -> context.getLong("minId"))
                .containsExactly(10L, 13L, 15L, 17L);
        assertThat(partitions.values()).extracting(context -> context.getLong("maxId"))
                .containsExactly(12L, 14L, 16L, 18L);
    }

    @Test
    void createsOneEmptyRangeWhenThereAreNoLocations() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT COALESCE(MIN(id), 1) FROM locations", Long.class))
                .thenReturn(1L);
        when(jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM locations", Long.class))
                .thenReturn(0L);

        Map<String, ExecutionContext> partitions = new LocationRangePartitioner(jdbcTemplate).partition(4);

        assertThat(partitions).containsOnlyKeys("location-partition-0");
        assertThat(partitions.get("location-partition-0").getLong("minId")).isEqualTo(1L);
        assertThat(partitions.get("location-partition-0").getLong("maxId")).isZero();
    }

    @Test
    void rejectsNonPositiveGridSize() {
        assertThatThrownBy(() -> new LocationRangePartitioner(mock(JdbcTemplate.class)).partition(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }
}
