package ru.bell.autoleasing.car.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class CarStatusConverter implements AttributeConverter<CarStatus, String> {

    @Override
    public String convertToDatabaseColumn(CarStatus status) {
        if (status == null) {
            return null;
        }
        return status.toString();
    }

    @Override
    public CarStatus convertToEntityAttribute(String s) {
        try {
            return CarStatus.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
