package ru.bell.autoleasing.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import ru.bell.autoleasing.client.enums.ClientType;
import ru.bell.autoleasing.client.model.dto.ClientDto;
import ru.bell.autoleasing.client.service.ClientService;
import ru.bell.autoleasing.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ClientServiceUpdateTest {

    @Autowired
    ClientService clientService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void updateWithoutLegalDataKeepsStoredLegalData() {
        Long id = clientService.save(new ClientDto(null, "Смирнов Андрей Петрович", "4510000001", "+79000000001",
                ClientType.INDIVIDUAL_ENTREPRENEUR, "500100000199", "ИП Смирнов А. П.")).id();

        clientService.update(new ClientDto(id, "Смирнов Андрей Петрович", "4510000001", "+79000000002",
                null, null, null));

        assertThat(jdbc.queryForObject("SELECT phone FROM autoleasing_core.clients WHERE id = ?", String.class, id))
                .isEqualTo("+79000000002");
        assertThat(jdbc.queryForObject("SELECT client_type FROM autoleasing_core.clients WHERE id = ?", String.class, id))
                .isEqualTo("ИП");
        assertThat(jdbc.queryForObject("SELECT inn FROM autoleasing_core.clients WHERE id = ?", String.class, id))
                .isEqualTo("500100000199");
        assertThat(jdbc.queryForObject("SELECT legal_name FROM autoleasing_core.clients WHERE id = ?", String.class, id))
                .isEqualTo("ИП Смирнов А. П.");
    }

    @Test
    void updateWithLegalDataReplacesStoredLegalData() {
        Long id = clientService.save(new ClientDto(null, "Козлов Роман Игоревич", "4510000003", "+79000000003",
                null, null, null)).id();

        clientService.update(new ClientDto(id, "Козлов Роман Игоревич", "4510000003", "+79000000003",
                ClientType.INDIVIDUAL_ENTREPRENEUR, "500100000200", "ИП Козлов Р. И."));

        assertThat(jdbc.queryForObject("SELECT client_type FROM autoleasing_core.clients WHERE id = ?", String.class, id))
                .isEqualTo("ИП");
        assertThat(jdbc.queryForObject("SELECT inn FROM autoleasing_core.clients WHERE id = ?", String.class, id))
                .isEqualTo("500100000200");
    }
}
