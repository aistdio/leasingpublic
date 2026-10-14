package ru.bell.autoleasing.contract;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;
import ru.bell.autoleasing.contract.repository.JpaContractRepository;
import ru.bell.autoleasing.payment.model.Payment;
import ru.bell.autoleasing.support.IntegrationTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ContractPaymentOrderTest {

    @Autowired
    JpaContractRepository contractRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void paymentsWithoutDueDateStayInPaymentNumberOrder() {
        Long contractId = insertContract("XTA00000000000001", "4510000011", "+79000000011");
        insertPayment(contractId, 3, null);
        insertPayment(contractId, 1, null);
        insertPayment(contractId, 2, null);

        assertThat(paymentNumbers(contractId)).containsExactly(1L, 2L, 3L);
    }

    @Test
    void paymentsWithDueDateAreOrderedByDueDate() {
        Long contractId = insertContract("XTA00000000000002", "4510000012", "+79000000012");
        insertPayment(contractId, 1, "2026-03-01");
        insertPayment(contractId, 2, "2026-02-01");

        assertThat(paymentNumbers(contractId)).containsExactly(2L, 1L);
    }

    private List<Long> paymentNumbers(Long contractId) {
        return transactionTemplate.execute(status -> contractRepository.findById(contractId).orElseThrow()
                .getPayments().stream()
                .map(Payment::getPaymentIdContractId)
                .map(id -> id.getPaymentId())
                .toList());
    }

    private Long insertContract(String vin, String passport, String phone) {
        jdbc.update("INSERT INTO autoleasing_core.cars (vin, brand, model, release_year, price, status) "
                + "VALUES (?, 'Test', 'Test', 2024, 1000000, 'доступен')", vin);
        Long clientId = jdbc.queryForObject("INSERT INTO autoleasing_core.clients (name, passport, phone) "
                + "VALUES ('Тестов Тест Тестович', ?, ?) RETURNING id", Long.class, passport, phone);
        return jdbc.queryForObject("INSERT INTO autoleasing_core.contracts "
                + "(car_vin, client_id, duration_in_months, down_payment, interest_rate, monthly_payment, status) "
                + "VALUES (?, ?, 12, 100000, 10, 50000, 'активен') RETURNING id", Long.class, vin, clientId);
    }

    private void insertPayment(Long contractId, long paymentId, String dueDate) {
        jdbc.update("INSERT INTO autoleasing_core.payments (contract_id, payment_id, amount, status, due_date) "
                + "VALUES (?, ?, 50000, 'не оплачен', ?::date)", contractId, paymentId, dueDate);
    }
}
