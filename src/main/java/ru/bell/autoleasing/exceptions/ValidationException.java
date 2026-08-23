package ru.bell.autoleasing.exceptions;

import lombok.Getter;

@Getter
public class ValidationException extends RuntimeException {
    private String fieldName;
    private String invalidValue;

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, String fieldName, String invalidValue) {
        super(message);
        this.fieldName = fieldName;
        this.invalidValue = invalidValue;
    }

    @Override
    public String getMessage() {
        if (fieldName != null && invalidValue != null) {
            return String.format("Ошибка валидации поля '%s': значение %s недопустимо.", fieldName, invalidValue);
        }
        return super.getMessage();
    }
}
