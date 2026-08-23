package ru.bell.autoleasing.payment.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class PaymentStatusConverter implements AttributeConverter<PaymentStatus, String> {

    @Override
    public String convertToDatabaseColumn(PaymentStatus status) {
        if (status == null) {
            return null;
        }
        return status.toString();
    }

    @Override
    public PaymentStatus convertToEntityAttribute(String s) {
        try {
            return PaymentStatus.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
