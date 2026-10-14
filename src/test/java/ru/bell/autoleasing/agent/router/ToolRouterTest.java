package ru.bell.autoleasing.agent.router;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRouterTest {
    private final ToolRouter router = new ToolRouter();

    @Test
    void paymentsQuestion() {
        RoutingDecision d = router.route("Сколько я заплатил в сентябре?");
        assertThat(d.allowedTools()).containsExactly("payments_summary");
        assertThat(d.rule()).isEqualTo("PAYMENTS");
    }

    @Test
    void scheduleQuestionWithLetterYo() {
        RoutingDecision d = router.route("Когда следующий платёж?");
        assertThat(d.allowedTools()).containsExactly("schedule");
        assertThat(d.rule()).isEqualTo("SCHEDULE");
    }

    @Test
    void contractsQuestion() {
        assertThat(router.route("Какой остаток долга по договору?").allowedTools()).containsExactly("contracts_overview");
    }

    @Test
    void severalIntentsAreCombined() {
        RoutingDecision d = router.route("Покажи график и сколько уже оплачено");
        assertThat(d.allowedTools()).containsExactly("payments_summary", "schedule");
        assertThat(d.rule()).isEqualTo("PAYMENTS+SCHEDULE");
    }

    @Test
    void upperCaseIsRecognised() {
        assertThat(router.route("ОПЛАТА ЗА МАЙ").allowedTools()).containsExactly("payments_summary");
    }

    @Test
    void unknownBlankAndNullFallBackToAllTools() {
        for (String q : new String[]{"Привет!", "   ", null}) {
            RoutingDecision d = router.route(q);
            assertThat(d.allowedTools()).containsExactly("contracts_overview", "payments_summary", "schedule");
            assertThat(d.rule()).isEqualTo("FALLBACK_ALL");
        }
    }
}
