package ru.bell.autoleasing.auth.jwt.login;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.bell.autoleasing.auth.jwt.login.dto.AuthenticationRequest;
import ru.bell.autoleasing.auth.jwt.login.dto.AuthenticationResponse;
import ru.bell.autoleasing.config.security.LoginAttemptService;
import ru.bell.autoleasing.exceptions.DefaultErrorResponse;
import ru.bell.autoleasing.user.AppUserPrincipal;
import ru.bell.autoleasing.utilities.ClientIp;
import ru.bell.autoleasing.utilities.security.JwtUtil;

@Slf4j
@RestController
@RequestMapping("api/auth")
@AllArgsConstructor
public class AuthenticationController {
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;

    @PostMapping("/login")
    public ResponseEntity<?> authenticate(@RequestBody AuthenticationRequest request, HttpServletRequest httpRequest) {
        log.debug("Попытка входа: {}", request);
        String username = request.getUsername();
        String ipAddress = ClientIp.getClientIP(httpRequest);

        ResponseEntity<DefaultErrorResponse> errorResponse = validateRateLimit(username, ipAddress);
        if (errorResponse != null) {
            return errorResponse;
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            UserDetails user = userDetailsService.loadUserByUsername(request.getUsername());
            String jwtToken = jwtUtil.generateToken(user);

            String role = ((AppUserPrincipal) user).role().name();

            return ResponseEntity.ok(new AuthenticationResponse(jwtToken, role));

        } catch (BadCredentialsException | DisabledException e) {
            int remainingAttemptsForUser = loginAttemptService.getRemainingAttempts(username);
            int remainingAttemptsForIp = loginAttemptService.getRemainingAttempts(ipAddress);
            int remainingAttempts = Math.min(remainingAttemptsForUser, remainingAttemptsForIp);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new DefaultErrorResponse("Неверные данные. Осталось попыток для входа: " + remainingAttempts + "."));

        }
    }

    private @Nullable ResponseEntity<DefaultErrorResponse> validateRateLimit(String username, String ipAddress) {
        if (loginAttemptService.isBlocked(username)) {
            long remainingBlockMinutes = loginAttemptService.getRemainingBlockMinutes(username);
            long remainingBlockSeconds = loginAttemptService.getRemainingBlockSeconds(username);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new DefaultErrorResponse(
                            String.format("Вход в аккаунт временно заблокирован в связи с большим количеством попыток входа. Попробуйте войти через %s минут %s секунд.", remainingBlockMinutes, remainingBlockSeconds)
                    ));
        }

        if (loginAttemptService.isBlocked(ipAddress)) {
            long remainingBlockMinutes = loginAttemptService.getRemainingBlockMinutes(ipAddress);
            long remainingBlockSeconds = loginAttemptService.getRemainingBlockSeconds(ipAddress);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new DefaultErrorResponse(
                            String.format("Слишком много неудачных попыток войти в аккаунт с вашего IP адреса. Попробуйте войти через %s минут %s секунд.", remainingBlockMinutes, remainingBlockSeconds)
                    ));
        }

        return null;
    }
}
