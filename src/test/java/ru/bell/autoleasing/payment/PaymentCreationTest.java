package ru.bell.autoleasing.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;
import ru.bell.autoleasing.contract.repository.JpaContractRepository;
import ru.bell.autoleasing.payment.service.PaymentService;
import ru.bell.autoleasing.support.IntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PaymentCreationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired JpaContractRepository contracts;
    @Autowired PaymentService paymentService;
    @Autowired TransactionTemplate tx;

    @Test
    void createsScheduleWithDatesAndComponents() {
        jdbc.update("INSERT INTO autoleasing_core.cars (vin, brand, model, release_year, price, status, version) "
                + "VALUES ('TESTVIN0000000009', 'Lada', 'Vesta', 2025, 1500000.00, 'в лизинге', 0)");
        jdbc.update("INSERT INTO autoleasing_core.clients (id, name, passport, phone) VALUES (77, 'Тест Тест Тестович', '4500000077', '+79000000077')");
        jdbc.update("INSERT INTO autoleasing_core.contracts (id, car_vin, client_id, duration_in_months, down_payment, interest_rate, "
                + "monthly_payment, status, start_date, contract_number) VALUES (501, 'TESTVIN0000000009', 77, 12, 300000.00, 12.00, "
                + "106618.55, 'активен', '2024-01-31', 'AL-2024-00501')");

        tx.executeWithoutResult(status -> paymentService.createPaymentsForContract(contracts.findById(501L).orElseThrow()));

        Map<String, Object> first = jdbc.queryForMap("SELECT * FROM autoleasing_core.payments WHERE contract_id = 501 AND payment_id = 1");
        assertThat(first.get("due_date").toString()).isEqualTo(LocalDate.of(2024, 2, 29).toString());
        assertThat((BigDecimal) first.get("amount")).isEqualByComparingTo("106618.55");
        assertThat((BigDecimal) first.get("markup")).isEqualByComparingTo("10000.00");
        assertThat(first.get("status")).isEqualTo("не оплачен");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM autoleasing_core.payments WHERE contract_id = 501", Integer.class)).isEqualTo(12);
    }
}
