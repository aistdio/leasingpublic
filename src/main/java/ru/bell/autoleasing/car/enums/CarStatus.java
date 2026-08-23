package ru.bell.autoleasing.car.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CarStatus {
    AVAILABLE("доступен"),
    LEASED("в лизинге");

    private final String description;

    CarStatus(String description) {
        this.description = description;
    }

    @JsonCreator
    public static CarStatus fromString(String text) {
        for (CarStatus status : CarStatus.values()) {
            if (status.toString().equalsIgnoreCase(text)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Не найден CarStatus: " + text);
    }

    @JsonValue
    @Override
    public String toString() {
        return description;
    }
}
