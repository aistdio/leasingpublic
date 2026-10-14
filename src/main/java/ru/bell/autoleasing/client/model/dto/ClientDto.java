package ru.bell.autoleasing.client.model.dto;

import jakarta.validation.constraints.NotBlank;
import ru.bell.autoleasing.client.enums.ClientType;

public record ClientDto(
        Long id,
        @NotBlank
        String name,
        @NotBlank
        String passport,
        @NotBlank
        String phone,
        ClientType clientType,
        String inn,
        String legalName
) {}
