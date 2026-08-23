package ru.bell.autoleasing.config.cache;

import lombok.extern.slf4j.Slf4j;
import org.ehcache.event.CacheEvent;
import org.ehcache.event.CacheEventListener;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
public class CacheEventLogger implements CacheEventListener<Object, Object> {

    @Value("${logging.level.org.hibernate.cache}")
    private boolean isLogging;

    @Override
    public void onEvent(CacheEvent<?, ?> cacheEvent) {
        if (isLogging) {
            log.trace("Событие L2 кэша типа {}: в L2 кэш с ключом {} записано новое значение {} вместо старого {}",
                    cacheEvent.getType(), cacheEvent.getKey(), cacheEvent.getNewValue(), cacheEvent.getOldValue());
        }
    }
}
