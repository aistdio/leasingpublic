package ru.bell.autoleasing.kafka;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@RequiredArgsConstructor
public class KafkaTopic {

    private final KafkaConfigData kafkaConfigData;

    @Bean
    public NewTopic cars() {
        return TopicBuilder.name(kafkaConfigData.getCarsTopic()).partitions(3).build();
    }

    @Bean
    public NewTopic clients() {
        return TopicBuilder.name(kafkaConfigData.getClientsTopic()).partitions(3).build();
    }

    @Bean
    public NewTopic contracts() {
        return TopicBuilder.name(kafkaConfigData.getContractsTopic()).partitions(3).build();
    }

    @Bean
    public NewTopic payments() {
        return TopicBuilder.name(kafkaConfigData.getPaymentsTopic()).partitions(3).build();
    }
}
