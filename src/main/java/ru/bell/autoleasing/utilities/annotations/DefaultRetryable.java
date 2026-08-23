package ru.bell.autoleasing.utilities.annotations;

import org.springframework.core.annotation.AliasFor;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retryable(maxAttemptsExpression = "${retry.maxAttempts}",
        backoff = @Backoff(delayExpression = "${retry.maxDelay}"))
public @interface DefaultRetryable {
    @AliasFor(annotation = Retryable.class, attribute = "retryFor")
    Class<? extends Throwable>[] retryFor() default {};
}
