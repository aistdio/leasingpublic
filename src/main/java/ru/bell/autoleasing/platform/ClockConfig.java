package ru.bell.autoleasing.platform;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${autolease.timezone:Europe/Moscow}") String zone,
                       @Value("${autolease.clock.fixed-date:}") String fixedDate) {
        ZoneId zoneId = ZoneId.of(zone);
        if (!fixedDate.isBlank()) {
            return Clock.fixed(LocalDate.parse(fixedDate).atTime(12, 0).atZone(zoneId).toInstant(), zoneId);
        }
        return Clock.system(zoneId);
    }
}
