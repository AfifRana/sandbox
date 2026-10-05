package com.example.weatherwatch.batch;

import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

public class LocationRangePartitioner implements Partitioner {

    private final JdbcTemplate jdbcTemplate;

    public LocationRangePartitioner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        if (gridSize < 1) {
            throw new IllegalArgumentException("Partition grid size must be positive");
        }

        long minimumId = jdbcTemplate.queryForObject("SELECT COALESCE(MIN(id), 1) FROM locations", Long.class);
        long maximumId = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM locations", Long.class);
        Map<String, ExecutionContext> partitions = new LinkedHashMap<>();

        if (maximumId < minimumId) {
            ExecutionContext emptyRange = new ExecutionContext();
            emptyRange.putLong("minId", 1);
            emptyRange.putLong("maxId", 0);
            partitions.put("location-partition-0", emptyRange);
            return partitions;
        }

        BigInteger start = BigInteger.valueOf(minimumId);
        BigInteger end = BigInteger.valueOf(maximumId);
        BigInteger span = end.subtract(start).add(BigInteger.ONE);
        BigInteger[] division = span.divideAndRemainder(BigInteger.valueOf(gridSize));
        BigInteger baseSize = division[0];
        int remainder = division[1].intValueExact();

        for (int index = 0; index < gridSize && start.compareTo(end) <= 0; index++) {
            BigInteger size = baseSize.add(index < remainder ? BigInteger.ONE : BigInteger.ZERO);
            BigInteger rangeEnd = start.add(size).subtract(BigInteger.ONE).min(end);
            ExecutionContext context = new ExecutionContext();
            context.putLong("minId", start.longValueExact());
            context.putLong("maxId", rangeEnd.longValueExact());
            partitions.put("location-partition-" + index, context);
            start = rangeEnd.add(BigInteger.ONE);
        }
        return partitions;
    }
}
