package ru.bell.autoleasing.client.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ClientTypeConverter implements AttributeConverter<ClientType, String> {

    @Override
    public String convertToDatabaseColumn(ClientType type) {
        if (type == null) {
            return null;
        }
        return type.toString();
    }

    @Override
    public ClientType convertToEntityAttribute(String s) {
        try {
            return ClientType.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
