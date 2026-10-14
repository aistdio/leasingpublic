package ru.bell.autoleasing.cabinet.dto;

public record MeResponse(String username, long clientId, String clientType, String legalName, String inn,
                         String contactName) {
}
