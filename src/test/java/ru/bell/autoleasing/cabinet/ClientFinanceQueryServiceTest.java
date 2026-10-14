package ru.bell.autoleasing.cabinet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import ru.bell.autoleasing.cabinet.dto.ContractSummary;
import ru.bell.autoleasing.cabinet.dto.PaymentRow;
import ru.bell.autoleasing.cabinet.dto.PaymentsSummary;
import ru.bell.autoleasing.cabinet.dto.UpcomingPayments;
import ru.bell.autoleasing.exceptions.NotFoundException;
import ru.bell.autoleasing.exceptions.ValidationException;
import ru.bell.autoleasing.payment.enums.PaymentStatus;
import ru.bell.autoleasing.support.IntegrationTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@Sql("/sql/finance-fixture.sql")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ClientFinanceQueryServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    @Autowired
    ClientFinanceQueryService finance;

    @Test
    void contractsShowRemainingDebtAndNextDueDate() {
        List<ContractSummary> contracts = finance.findContracts(1001, TODAY);

        assertThat(contracts).extracting(ContractSummary::contractId).containsExactly(2002L, 2001L);
        ContractSummary active = contracts.get(1);
        assertThat(active.remainingDebt()).isEqualByComparingTo("30000.00");
        assertThat(active.unpaidCount()).isEqualTo(3);
        assertThat(active.nextDueDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(active.car()).isEqualTo("Haval Jolion");
        assertThat(active.status()).isEqualTo("ACTIVE");
        ContractSummary closed = contracts.get(0);
        assertThat(closed.remainingDebt()).isEqualByComparingTo("0.00");
        assertThat(closed.nextDueDate()).isNull();
    }

    @Test
    void summaryIncludesBothPeriodEndsAndPenalty() {
        PaymentsSummary september = finance.summarize(1001, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        assertThat(september.paidTotal()).isEqualByComparingTo("20030.00");
        assertThat(september.paidCount()).isEqualTo(2);
        assertThat(september.accruedTotal()).isEqualByComparingTo("10000.00");
        assertThat(september.accruedCount()).isEqualTo(1);

        PaymentsSummary february = finance.summarize(1001, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        assertThat(february.paidTotal()).isEqualByComparingTo("10000.00");
        assertThat(february.accruedTotal()).isEqualByComparingTo("5000.00");
    }

    @Test
    void cancelledPaymentsAreNotAccrued() {
        PaymentsSummary april = finance.summarize(1001, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
        assertThat(april.accruedTotal()).isEqualByComparingTo("0.00");
        assertThat(april.accruedCount()).isZero();
    }

    @Test
    void clientWithoutContractsGetsEmptyResults() {
        assertThat(finance.findContracts(1003, TODAY)).isEmpty();
        PaymentsSummary year = finance.summarize(1003, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertThat(year.paidTotal()).isEqualByComparingTo("0.00");
        assertThat(year.accruedTotal()).isEqualByComparingTo("0.00");
        assertThat(finance.findUpcoming(1003, null, 3, TODAY).nextPayment()).isNull();
    }

    @Test
    void reversedPeriodIsRejected() {
        assertThatThrownBy(() -> finance.summarize(1001, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void paymentsFilterByDueDateAndStatus() {
        List<PaymentRow> rows = finance.findPayments(1001, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31), null, TODAY);
        assertThat(rows).extracting(PaymentRow::paymentId).containsExactly(3L, 4L);
        List<PaymentRow> unpaid = finance.findPayments(1001, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31), PaymentStatus.UNPAID, TODAY);
        assertThat(unpaid).extracting(PaymentRow::paymentId).containsExactly(4L);
    }

    @Test
    void upcomingSeparatesOverdueFromNextPayment() {
        UpcomingPayments upcoming = finance.findUpcoming(1002, null, 3, TODAY);
        assertThat(upcoming.nextPayment().dueDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(upcoming.upcoming()).hasSize(3);
        assertThat(upcoming.upcoming().get(0).overdue()).isTrue();
        assertThat(upcoming.overdueCount()).isEqualTo(1);
        assertThat(upcoming.overdueTotal()).isEqualByComparingTo("20000.00");
        assertThat(finance.findUpcoming(1001, null, 1, TODAY).upcoming()).hasSize(1);
    }

    @Test
    void otherClientsContractIsNotFound() {
        assertThatThrownBy(() -> finance.findSchedule(1002, 2001, TODAY)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> finance.findUpcoming(1001, 2003L, 3, TODAY)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void limitOutOfRangeIsRejected() {
        assertThatThrownBy(() -> finance.findUpcoming(1001, null, 0, TODAY)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> finance.findUpcoming(1001, null, 13, TODAY)).isInstanceOf(ValidationException.class);
    }
}
