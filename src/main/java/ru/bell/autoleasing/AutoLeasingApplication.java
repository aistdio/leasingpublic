package ru.bell.autoleasing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import ru.bell.autoleasing.utilities.DatabaseInit;

@SpringBootApplication
public class AutoLeasingApplication {

    public static void main(String[] args) {
        DatabaseInit.createDatabaseIfNotExists();
        SpringApplication.run(AutoLeasingApplication.class);
    }
}