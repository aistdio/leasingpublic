package ru.bell.autoleasing.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;

@ConfigurationProperties("autolease.seed")
public record SeedProperties(boolean enabled, long seed, LocalDate asOf, String clientPassword, String managerPassword) {
}
