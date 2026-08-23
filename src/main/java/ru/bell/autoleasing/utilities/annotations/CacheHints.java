package ru.bell.autoleasing.utilities.annotations;

import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.QueryHints;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
@QueryHints({
        @QueryHint(name = "org.hibernate.cacheable", value = "true"),
        @QueryHint(name = "jakarta.persistence.cache.retrieveMode", value = "USE"),
        @QueryHint(name = "jakarta.persistence.cache.storeMode", value = "USE")
})
public @interface CacheHints {
}
