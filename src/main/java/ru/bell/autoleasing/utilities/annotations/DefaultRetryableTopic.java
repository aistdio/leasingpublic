package ru.bell.autoleasing.utilities.annotations;

import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
@RetryableTopic(
        backoff = @Backoff(delayExpression = "${kafka.retry.maxDelay}", multiplier = 2.0),
        attempts = "${kafka.retry.maxAttempts}",
        autoCreateTopics = "true",
        include = ObjectOptimisticLockingFailureException.class
)
public @interface DefaultRetryableTopic {
}
