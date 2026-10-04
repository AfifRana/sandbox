package com.example.weatherwatch.location;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "weather.kafka.create-topic", havingValue = "true", matchIfMissing = true)
public class LocationEventConfiguration {

    @Bean
    NewTopic locationEventsTopic(
            @Value("${weather.kafka.location-events-topic:location-events}") String topicName
    ) {
        return TopicBuilder.name(topicName).partitions(3).replicas(1).build();
    }
}
