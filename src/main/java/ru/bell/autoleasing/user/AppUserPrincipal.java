package ru.bell.autoleasing.user;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public record AppUserPrincipal(Long id, String username, String passwordHash, UserRole role, Long clientId,
                               boolean enabled) implements UserDetails {

    public static AppUserPrincipal from(AppUser user) {
        return new AppUserPrincipal(user.getId(), user.getUsername(), user.getPasswordHash(), user.getRole(),
                user.getClientId(), user.isEnabled());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
