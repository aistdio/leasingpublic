package ru.bell.autoleasing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.bell.autoleasing.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class ApplicationContextTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void contextStartsAndFlywayCreatesLegacyTables() {
        Integer tables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'autoleasing_core'",
                Integer.class);
        assertThat(tables).isGreaterThanOrEqualTo(4);
    }
}
