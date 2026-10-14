package ru.bell.autoleasing.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import ru.bell.autoleasing.support.IntegrationTest;

import java.time.Clock;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class PlatformEndpointsTest {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    Clock clock;

    @Test
    void infoIsPublicAndShowsEnvironmentAndVersion() throws Exception {
        mockMvc.perform(get("/api/public/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appEnv").value("test"))
                .andExpect(jsonPath("$.version").isNotEmpty());
    }

    @Test
    void generatesRequestIdWhenMissing() throws Exception {
        mockMvc.perform(get("/api/public/info"))
                .andExpect(header().string(RequestIdFilter.HEADER, matchesPattern(UUID_PATTERN)));
    }

    @Test
    void echoesValidRequestId() throws Exception {
        mockMvc.perform(get("/api/public/info").header(RequestIdFilter.HEADER, "qa-check-001"))
                .andExpect(header().string(RequestIdFilter.HEADER, "qa-check-001"));
    }

    @Test
    void replacesInvalidRequestId() throws Exception {
        mockMvc.perform(get("/api/public/info").header(RequestIdFilter.HEADER, "bad id <script>"))
                .andExpect(header().string(RequestIdFilter.HEADER, not("bad id <script>")))
                .andExpect(header().string(RequestIdFilter.HEADER, matchesPattern(UUID_PATTERN)));
    }

    @Test
    void openApiIsPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("AutoLease API"));
    }

    @Test
    void clockIsFixedInTests() {
        assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, 10, 10));
    }
}
