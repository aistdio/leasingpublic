package ru.bell.autoleasing.cabinet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import ru.bell.autoleasing.support.IntegrationTest;
import ru.bell.autoleasing.support.TestPrincipals;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@Sql("/sql/finance-fixture.sql")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class CabinetControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void profileShowsLegalData() throws Exception {
        mockMvc.perform(get("/api/me").with(user(TestPrincipals.client(1001))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalName").value("ООО «Альфа Тест»"))
                .andExpect(jsonPath("$.clientType").value("ООО"))
                .andExpect(jsonPath("$.inn").value("7701000019"));
    }

    @Test
    void contractsListIsOwnOnly() throws Exception {
        mockMvc.perform(get("/api/me/contracts").with(user(TestPrincipals.client(1001))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].remainingDebt").value(30000.0));
    }

    @Test
    void otherClientsScheduleIsNotFound() throws Exception {
        mockMvc.perform(get("/api/me/contracts/2003/schedule").with(user(TestPrincipals.client(1001))))
                .andExpect(status().isNotFound());
    }

    @Test
    void summaryForSeptember() throws Exception {
        mockMvc.perform(get("/api/me/payments/summary").param("from", "2026-09-01").param("to", "2026-09-30")
                        .with(user(TestPrincipals.client(1001))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidTotal").value(20030.0))
                .andExpect(jsonPath("$.accruedTotal").value(10000.0));
    }

    @Test
    void badParametersAreBadRequest() throws Exception {
        var client = user(TestPrincipals.client(1001));
        mockMvc.perform(get("/api/me/payments/summary").param("from", "2026-10-01").param("to", "2026-09-01").with(client))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
        mockMvc.perform(get("/api/me/payments").param("from", "2026-13-01").param("to", "2026-12-31").with(client))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/me/payments").param("from", "2026-01-01").with(client))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/me/payments").param("from", "2026-01-01").param("to", "2026-12-31").param("status", "оплачен").with(client))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/me/payments/upcoming").param("limit", "50").with(client))
                .andExpect(status().isBadRequest());
    }

    @Test
    void managerAndAnonymousCannotUseCabinet() throws Exception {
        mockMvc.perform(get("/api/me").with(user(TestPrincipals.manager()))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }
}
