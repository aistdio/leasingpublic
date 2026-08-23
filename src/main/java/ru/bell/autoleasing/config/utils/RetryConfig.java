package ru.bell.autoleasing.config.utils;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.retry.annotation.EnableRetry;

@Configuration
@EnableRetry
@PropertySource("classpath:retryConfig.properties")
public class RetryConfig {
}
