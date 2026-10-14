package ru.bell.autoleasing.config.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.bell.autoleasing.utilities.security.JwtUtil;

import java.io.IOException;

@Slf4j
@Component
@AllArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal (@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain filterChain)
            throws ServletException, IOException {
        log.debug("Обработка JWT для пути: {}", request.getRequestURI());

        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            log.debug("Путь не начинается с '/api/' - JWT не будет обработан");
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = extractToken(request);
        if (jwt == null) {
            log.debug("JWT не был извлечен для пути: {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        String username;
        try {
            username = jwtUtil.extractUsername(jwt);
            log.debug("Из JWT было извлечено имя пользователя: {}", username);
        } catch (ExpiredJwtException e) {
            log.warn("Истек срок действия JWT");
            filterChain.doFilter(request, response);
            return;
        } catch (MalformedJwtException e) {
            log.warn("Неверная структура JWT");
            filterChain.doFilter(request, response);
            return;
        } catch (SignatureException e) {
            log.warn("Неверная JWT подпись");
            filterChain.doFilter(request, response);
            return;
        } catch (IllegalArgumentException e) {
            log.warn("Пустая строка компонентов JWT");
            filterChain.doFilter(request, response);
            return;
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails;
            try {
                userDetails = this.userDetailsService.loadUserByUsername(username);
            } catch (UsernameNotFoundException e) {
                log.warn("Пользователь из JWT не найден: {}", username);
                filterChain.doFilter(request, response);
                return;
            }

            if (jwtUtil.isTokenValid(jwt, userDetails)) {
                log.debug("JWT валиден для пользователя: {}", username);
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } else {
                log.warn("JWT невалиден для пользователя: {}", username);
            }
        }

        filterChain.doFilter(request, response);
    }

    private static String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
