package ru.bell.autoleasing.client.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
            log.warn("Не удалось преобразовать значение столбца client_type: '{}'", s);
            return null;
        }
    }
}
