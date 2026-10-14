package ru.bell.autoleasing.seed;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import ru.bell.autoleasing.support.IntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class SeedDataWriterTest {

    @Autowired SeedDataWriter writer;
    @Autowired JdbcTemplate jdbc;

    @Test
    void writesEverythingAndResetsIdentitySequences() {
        SeedData data = new SeedDataGenerator(20261014L, LocalDate.of(2026, 10, 1)).generate();

        writer.write(data, "client-hash", "manager-hash");

        assertThat(count("autoleasing_core.cars")).isEqualTo(70);
        assertThat(count("autoleasing_core.clients")).isEqualTo(30);
        assertThat(count("autoleasing_core.contracts")).isEqualTo(60);
        assertThat(count("autoleasing_core.payments")).isEqualTo(data.payments().size());
        assertThat(count("autoleasing_core.app_users")).isEqualTo(31);
        BigDecimal expected = data.payments().stream().map(SeedData.PaymentSeed::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM autoleasing_core.payments", BigDecimal.class)).isEqualByComparingTo(expected);
        Long newId = jdbc.queryForObject("INSERT INTO autoleasing_core.clients (name, passport, phone) "
                + "VALUES ('Новый Клиент Тестович', '4599999999', '+79999999999') RETURNING id", Long.class);
        assertThat(newId).isEqualTo(31L);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
