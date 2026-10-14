package ru.bell.autoleasing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AutoLeasingApplication {
    public static void main(String[] args) {
        SpringApplication.run(AutoLeasingApplication.class, args);
    }
}
