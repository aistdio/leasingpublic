package ru.bell.autoleasing.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppUserPrincipalTest {

    @Test
    void toStringDoesNotRevealPasswordHash() {
        AppUserPrincipal principal = new AppUserPrincipal(7L, "client7", "$2a$10$secretHashValue", UserRole.CLIENT, 7L, true);

        String text = principal.toString();

        assertThat(text).doesNotContain("$2a$10$secretHashValue");
        assertThat(text).contains("client7");
    }
}
