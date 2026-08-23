package ru.bell.autoleasing.config.security;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import jakarta.annotation.PostConstruct;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service
public class LoginAttemptService {
    @Value("${security.rate-limit.max-attempts}")
    private int maxAttempts;

    @Value("${security.rate-limit.block-duration-minutes}")
    private int blockDurationMinutes;

    private LoadingCache<@NotNull String, @NotNull AttemptInfo> attemptsCache;

    private record AttemptInfo(int attempts, long blockedUntilMillis) {
        static AttemptInfo empty() { return new AttemptInfo(0, 0L); }
    }

    @PostConstruct
    public void init() {
        attemptsCache = CacheBuilder.newBuilder()
                .expireAfterWrite(blockDurationMinutes, TimeUnit.MINUTES)
                .build(new CacheLoader<>() {
                    @Override
                    public AttemptInfo load(String key) {
                        return AttemptInfo.empty();
                    }
                });
    }

    public void loginSucceeded(String key) {
        attemptsCache.invalidate(key);
    }

    public void loginFailed(String key) {
        AttemptInfo info;
        try {
            info = attemptsCache.get(key);
        } catch (ExecutionException e) {
            info = AttemptInfo.empty();
        }

        int attempts = info.attempts() + 1;
        long blockedUntil = info.blockedUntilMillis();

        if (attempts >= maxAttempts && blockedUntil <= 0) {
            blockedUntil = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(blockDurationMinutes);
        }

        attemptsCache.put(key, new AttemptInfo(attempts, blockedUntil));
    }

    public boolean isBlocked(String key) {
        try {
            return attemptsCache.get(key).attempts >= maxAttempts;
        } catch (ExecutionException e) {
            return false;
        }
    }

    public int getRemainingAttempts(String key) {
        try {
            int attempts = attemptsCache.get(key).attempts();
            return Math.max(0, maxAttempts - attempts);
        } catch (ExecutionException e) {
            return maxAttempts;
        }
    }

    public long getRemainingBlockMinutes(String key) {
        try {
            long now = System.currentTimeMillis();
            long blockedUntil = attemptsCache.get(key).blockedUntilMillis();
            if (blockedUntil <= now) return 0L;
            return TimeUnit.MILLISECONDS.toMinutes(blockedUntil - now);
        } catch (ExecutionException e) {
            return 0L;
        }
    }

    public long getRemainingBlockSeconds(String key) {
        try {
            long now = System.currentTimeMillis();
            long blockedUntil = attemptsCache.get(key).blockedUntilMillis();
            if (blockedUntil <= now) return 0L;
            return TimeUnit.MILLISECONDS.toSeconds(blockedUntil - now);
        } catch (ExecutionException e) {
            return 0L;
        }
    }
}
