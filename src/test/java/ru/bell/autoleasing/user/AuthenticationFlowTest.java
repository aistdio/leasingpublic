package ru.bell.autoleasing.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import ru.bell.autoleasing.support.IntegrationTest;
import ru.bell.autoleasing.support.TestPrincipals;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AuthenticationFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;

    private void createManager(String username, String password) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(password));
        user.setRole(UserRole.MANAGER);
        user.setEnabled(true);
        users.save(user);
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("token").asText();
    }

    @Test
    void managerLogsInAndReceivesRole() throws Exception {
        createManager("manager_t", "Secret123!");
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"manager_t\",\"password\":\"Secret123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        createManager("manager_w", "Secret123!");
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"manager_w\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenOpensManagerApi() throws Exception {
        createManager("manager_k", "Secret123!");
        String token = login("manager_k", "Secret123!");
        mockMvc.perform(get("/api/clients").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousApiCallIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/clients")).andExpect(status().isUnauthorized());
    }

    @Test
    void garbageTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/clients").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenOfDeletedUserIsUnauthorized() throws Exception {
        createManager("manager_d", "Secret123!");
        String token = login("manager_d", "Secret123!");
        users.deleteAll();
        mockMvc.perform(get("/api/clients").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientCannotUseManagerApi() throws Exception {
        mockMvc.perform(get("/api/clients").with(user(TestPrincipals.client(1))))
                .andExpect(status().isForbidden());
    }

    @Test
    void staticResourcesArePublic() throws Exception {
        mockMvc.perform(get("/api/public/info")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }
}
