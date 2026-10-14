package ru.bell.autoleasing.payment.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum PaymentStatus {
    PAID("оплачен"),
    UNPAID("не оплачен"),
    CANCELLED("отменен");

    private final String description;

    PaymentStatus(String description) {
        this.description = description;
    }

    @JsonCreator
    public static PaymentStatus fromString(String text) {
        for (PaymentStatus status : PaymentStatus.values()) {
            if (status.getDescription().equalsIgnoreCase(text)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Не найден PaymentStatus: " + text);
    }

    @JsonValue
    @Override
    public String toString() {
        return description;
    }
}
