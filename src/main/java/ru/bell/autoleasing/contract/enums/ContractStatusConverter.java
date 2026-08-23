package ru.bell.autoleasing.contract.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ContractStatusConverter implements AttributeConverter<ContractStatus, String> {

    @Override
    public String convertToDatabaseColumn(ContractStatus status) {
        if (status == null) {
            return null;
        }
        return status.toString();
    }

    @Override
    public ContractStatus convertToEntityAttribute(String s) {
        try {
            return ContractStatus.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
