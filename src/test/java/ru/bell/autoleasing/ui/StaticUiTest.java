package ru.bell.autoleasing.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import ru.bell.autoleasing.support.IntegrationTest;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class StaticUiTest {

    @Autowired
    MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {"/index.html", "/cabinet.html", "/css/app.css", "/js/api.js", "/js/login.js",
            "/js/cabinet.js", "/js/env-badge.js", "/manifest.json", "/sw.js", "/icons/icon-192.png", "/icons/icon-512.png"})
    void staticFilesArePublic(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isOk());
    }

    @Test
    void loginPageHasTestIds() throws Exception {
        String html = mockMvc.perform(get("/index.html")).andReturn().getResponse().getContentAsString();
        for (String id : new String[]{"env-badge", "login-username", "login-password", "login-submit", "login-error"}) {
            org.assertj.core.api.Assertions.assertThat(html).contains("data-testid=\"" + id + "\"");
        }
    }

    @Test
    void cabinetPageHasTestIds() throws Exception {
        String html = mockMvc.perform(get("/cabinet.html")).andReturn().getResponse().getContentAsString();
        for (String id : new String[]{"client-name", "logout-button", "tab-contracts", "tab-schedule", "tab-payments",
                "contracts-table", "contracts-empty", "schedule-contract-select", "schedule-table", "payments-from",
                "payments-to", "payments-status", "payments-apply", "payments-total", "payments-table"}) {
            org.assertj.core.api.Assertions.assertThat(html).contains("data-testid=\"" + id + "\"");
        }
    }

    @Test
    void pagesHaveNoInlineScriptsAndSendContentSecurityPolicy() throws Exception {
        mockMvc.perform(get("/cabinet.html"))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("<script>"))));
    }
}
