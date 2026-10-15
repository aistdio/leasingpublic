package ru.bell.autoleasing.agent.core;

import lombok.Getter;

import java.util.UUID;

@Getter
public class AgentUnavailableException extends RuntimeException {
    private final UUID traceId;

    public AgentUnavailableException(UUID traceId, Throwable cause) {
        super("Ассистент временно недоступен, попробуйте позже", cause);
        this.traceId = traceId;
    }
}
