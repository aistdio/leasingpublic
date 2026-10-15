package ru.bell.autoleasing.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.bell.autoleasing.support.IntegrationTest;
import ru.bell.autoleasing.support.TestPrincipals;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class HttpErrorStatusTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void plainTextRequestBodyIsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/agent/ask").with(user(TestPrincipals.client(1001)))
                        .contentType(MediaType.TEXT_PLAIN).content("Когда следующий платёж?"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Тип содержимого запроса не поддерживается. Используйте application/json."));
    }

    @Test
    void xmlRequestBodyIsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/agent/ask").with(user(TestPrincipals.client(1001)))
                        .contentType(MediaType.APPLICATION_XML).content("<question>Когда следующий платёж?</question>"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void unsupportedMethodOnExistingEndpointIsMethodNotAllowed() throws Exception {
        mockMvc.perform(get("/api/agent/ask").with(user(TestPrincipals.client(1001))))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.message").value("Метод GET не поддерживается."));
    }
}
