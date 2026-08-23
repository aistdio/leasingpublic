package ru.bell.autoleasing.exceptions;

public class DataAccessException extends RuntimeException {
    public DataAccessException(String message) {
        super(message);
    }
}