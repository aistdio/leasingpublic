package ru.bell.autoleasing.payment.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreatePaymentDto(
        @NotNull
        @Min(1L)
        Long contractId,
        @NotNull
        @DecimalMin("0")
        BigDecimal amount
) {}
