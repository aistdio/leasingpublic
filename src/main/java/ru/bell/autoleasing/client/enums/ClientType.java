package ru.bell.autoleasing.client.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum ClientType {
    LLC("ООО"),
    INDIVIDUAL_ENTREPRENEUR("ИП");

    private final String description;

    ClientType(String description) {
        this.description = description;
    }

    @JsonCreator
    public static ClientType fromString(String text) {
        for (ClientType type : values()) {
            if (type.description.equalsIgnoreCase(text) || type.name().equalsIgnoreCase(text)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Не найден ClientType: " + text);
    }

    @JsonValue
    @Override
    public String toString() {
        return description;
    }
}
