package ru.bell.autoleasing.kafka;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
public class KafkaConfigData {

    @Value("${spring.kafka.bootstrap-servers}")
    private List<String> bootstrapServers;

    @Value("${kafka.topic-names.cars}")
    private String carsTopic;

    @Value("${kafka.topic-names.clients}")
    private String clientsTopic;

    @Value("${kafka.topic-names.contracts}")
    private String contractsTopic;

    @Value("${kafka.topic-names.payments}")
    private String paymentsTopic;
}
