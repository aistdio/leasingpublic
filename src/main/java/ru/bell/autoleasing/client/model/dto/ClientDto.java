package ru.bell.autoleasing.client.model.dto;

import jakarta.validation.constraints.NotBlank;

public record ClientDto(
        Long id,
        @NotBlank
        String name,
        @NotBlank
        String passport,
        @NotBlank
        String phone
) {}
