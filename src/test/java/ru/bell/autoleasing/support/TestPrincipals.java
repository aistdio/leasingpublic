package ru.bell.autoleasing.support;

import ru.bell.autoleasing.user.AppUserPrincipal;
import ru.bell.autoleasing.user.UserRole;

public final class TestPrincipals {
    private TestPrincipals() {
    }

    public static AppUserPrincipal client(long clientId) {
        return new AppUserPrincipal(clientId, "client" + clientId, "not-used", UserRole.CLIENT, clientId, true);
    }

    public static AppUserPrincipal manager() {
        return new AppUserPrincipal(9999L, "manager", "not-used", UserRole.MANAGER, null, true);
    }
}
