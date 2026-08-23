package ru.bell.autoleasing.contract.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum ContractStatus {
    ACTIVE("активен"),
    CLOSED("закрыт");

    private final String description;

    ContractStatus(String description) {
        this.description = description;
    }

    @JsonCreator
    public static ContractStatus fromString(String text) {
        for (ContractStatus status : ContractStatus.values()) {
            if (status.getDescription().equalsIgnoreCase(text)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Не найден ContractStatus: " + text);
    }

    @JsonValue
    @Override
    public String toString() {
        return getDescription();
    }
}
