package ru.bell.autoleasing.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "autolease.seed.enabled", havingValue = "true")
public class SeedRunner implements ApplicationRunner {
    private final SeedProperties properties;
    private final SeedDataWriter writer;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    @Override
    public void run(ApplicationArguments args) {
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM autoleasing_core.clients", Integer.class);
        if (existing != null && existing > 0) {
            log.info("Генерация данных пропущена: клиентов уже {}", existing);
            return;
        }
        SeedData data = new SeedDataGenerator(properties.seed(), properties.asOf()).generate();
        writer.write(data, encoder.encode(properties.clientPassword()), encoder.encode(properties.managerPassword()));
        log.info("Сгенерированы данные: автомобилей={}, клиентов={}, договоров={}, платежей={}, пользователей={}",
                data.cars().size(), data.clients().size(), data.contracts().size(), data.payments().size(), data.users().size());
    }
}
