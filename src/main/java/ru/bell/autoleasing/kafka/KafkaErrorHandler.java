package ru.bell.autoleasing.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class KafkaErrorHandler {

    @Bean
    public DefaultErrorHandler errorHandler() {
        FixedBackOff fixedBackOff = new FixedBackOff(1000L, 3);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(fixedBackOff);

        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("Ошибка обработки записи: {}, попытка номер {}. Исключение: {}.",
                        record.value(), deliveryAttempt, ex.getMessage()));
        return errorHandler;
    }
}
