package ru.bell.autoleasing.agent.tool;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import ru.bell.autoleasing.cabinet.dto.ContractSummary;
import ru.bell.autoleasing.cabinet.dto.PaymentsSummary;
import ru.bell.autoleasing.cabinet.dto.UpcomingPayments;
import ru.bell.autoleasing.exceptions.NotFoundException;
import ru.bell.autoleasing.support.IntegrationTest;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@Sql("/sql/finance-fixture.sql")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AgentToolsTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    @Autowired
    ToolRegistry registry;

    private Object run(String tool, Map<String, Object> args, long clientId) {
        return registry.find(tool).orElseThrow().execute(args, new ToolContext(clientId, TODAY));
    }

    @Test
    void registryHasThreeToolsSortedByName() {
        assertThat(registry.names()).containsExactly("contracts_overview", "payments_summary", "schedule");
        assertThat(registry.specs(registry.names())).allSatisfy(spec -> assertThat(spec.parameters().get("type")).isEqualTo("object"));
    }

    @Test
    void paymentsSummaryUsesInclusivePeriod() {
        PaymentsSummary s = (PaymentsSummary) run("payments_summary",
                Map.of("period_from", "2026-09-01", "period_to", "2026-09-30"), 1001);
        assertThat(s.paidTotal()).isEqualByComparingTo("20030.00");
        assertThat(s.paidCount()).isEqualTo(2);
        assertThat(s.accruedTotal()).isEqualByComparingTo("10000.00");
    }

    @Test
    void paymentsSummaryNeedsBothDatesInOrder() {
        assertThatThrownBy(() -> run("payments_summary", Map.of("period_from", "2026-09-01"), 1001))
                .isInstanceOf(ToolArgumentException.class);
        assertThatThrownBy(() -> run("payments_summary", Map.of("period_from", "2026-10-01", "period_to", "2026-09-01"), 1001))
                .isInstanceOf(ToolArgumentException.class);
    }

    @Test
    void scheduleReturnsNextPaymentAndOverdue() {
        UpcomingPayments u = (UpcomingPayments) run("schedule", Map.of(), 1002);
        assertThat(u.nextPayment().dueDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(u.overdueCount()).isEqualTo(1);
        assertThat(u.overdueTotal()).isEqualByComparingTo("20000.00");
    }

    @Test
    void scheduleAcceptsContractIdAsString() {
        UpcomingPayments u = (UpcomingPayments) run("schedule", Map.of("contract_id", "2001", "limit", 2), 1001);
        assertThat(u.nextPayment().paymentId()).isEqualTo(4L);
        assertThat(u.upcoming()).hasSize(2);
    }

    @Test
    void scheduleRefusesOtherClientsContract() {
        assertThatThrownBy(() -> run("schedule", Map.of("contract_id", 2003), 1001)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void contractsOverviewIgnoresClientIdArgument() {
        ContractsOverview o = (ContractsOverview) run("contracts_overview", Map.of("client_id", 1002), 1001);
        assertThat(o.contracts()).extracting(ContractSummary::contractId).containsExactly(2002L, 2001L);
        assertThat(o.totalRemainingDebt()).isEqualByComparingTo("30000.00");
    }

    @Test
    void clientWithoutContractsGetsEmptyOverview() {
        ContractsOverview o = (ContractsOverview) run("contracts_overview", Map.of(), 1003);
        assertThat(o.contracts()).isEmpty();
        assertThat(o.totalRemainingDebt()).isEqualByComparingTo("0.00");
        assertThat(((UpcomingPayments) run("schedule", Map.of(), 1003)).nextPayment()).isNull();
    }
}
