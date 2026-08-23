package ru.bell.autoleasing.config.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ru.bell.autoleasing.utilities.ClientIp;

@Slf4j
@Component
@AllArgsConstructor
public class AuthenticationEventListener {
    private final LoginAttemptService loginAttemptService;

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        String ipAddress = getClientIP();

        log.info("Успешный вход для пользователя: {} с IP: {}", username, ipAddress);
        loginAttemptService.loginSucceeded(username);
        loginAttemptService.loginSucceeded(ipAddress);
    }

    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        String ipAddress = getClientIP();
        int remainingAttemptsForUsername = loginAttemptService.getRemainingAttempts(username);
        int remainingAttemptsForIp = loginAttemptService.getRemainingAttempts(ipAddress);

        loginAttemptService.loginFailed(username);
        loginAttemptService.loginFailed(ipAddress);
        log.warn("Неудачная попытка входа для пользователя: {} с IP: {}. " +
                "Для пользователя {} осталось {} попыток для входа. " +
                "Для IP адреса {} осталось {} попыток для входа.",
                username, ipAddress, username, remainingAttemptsForUsername, ipAddress, remainingAttemptsForIp);
    }

    private String getClientIP() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = attributes.getRequest();

        return ClientIp.getClientIP(request);
    }
}
