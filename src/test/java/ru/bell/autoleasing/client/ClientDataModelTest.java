package ru.bell.autoleasing.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import ru.bell.autoleasing.client.enums.ClientType;
import ru.bell.autoleasing.client.model.Client;
import ru.bell.autoleasing.client.repository.ClientRepository;
import ru.bell.autoleasing.client.service.ClientService;
import ru.bell.autoleasing.payment.enums.PaymentStatus;
import ru.bell.autoleasing.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ClientDataModelTest {

    @Autowired
    ClientRepository clientRepository;

    @Autowired
    ClientService clientService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void savesLegalDataAndWritesAuditRow() {
        Client client = new Client();
        client.setName("Смирнов Андрей Петрович");
        client.setPassport("4510000001");
        client.setPhone("+79000000001");
        client.setClientType(ClientType.INDIVIDUAL_ENTREPRENEUR);
        client.setInn("500100000199");
        client.setLegalName("ИП Смирнов А. П.");

        Long id = clientRepository.save(client).getId();

        assertThat(jdbc.queryForObject("SELECT client_type FROM autoleasing_core.clients WHERE id = ?", String.class, id))
                .isEqualTo("ИП");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM autoleasing_core_history.clients_aud WHERE id = ? AND inn = '500100000199'",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    void clientTypeDefaultsToLimitedLiabilityCompany() {
        Client client = new Client();
        client.setName("Козлов Роман Игоревич");
        client.setPassport("4510000003");
        client.setPhone("+79000000003");

        Long id = clientRepository.save(client).getId();

        assertThat(jdbc.queryForObject("SELECT client_type FROM autoleasing_core.clients WHERE id = ?", String.class, id))
                .isEqualTo("ООО");
    }

    @Test
    void cancelledPaymentStatusIsStoredAsRussianText() {
        assertThat(PaymentStatus.fromString("отменен")).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(PaymentStatus.CANCELLED.toString()).isEqualTo("отменен");
    }

    @Test
    void clientRoleRequiresClientId() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO autoleasing_core.app_users (username, password_hash, role) VALUES ('nobody', 'x', 'CLIENT')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingClientAlsoDeletesItsLogin() {
        Client client = new Client();
        client.setName("Орлов Виктор Сергеевич");
        client.setPassport("4510000005");
        client.setPhone("+79000000005");
        Long id = clientRepository.save(client).getId();
        jdbc.update("INSERT INTO autoleasing_core.app_users (username, password_hash, role, client_id) VALUES ('client_login', 'x', 'CLIENT', ?)", id);

        clientService.deleteById(id);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM autoleasing_core.clients WHERE id = ?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM autoleasing_core.app_users WHERE username = 'client_login'", Integer.class)).isZero();
    }
}
