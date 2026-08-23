package ru.bell.autoleasing.payment.model.dto;

import jakarta.validation.constraints.NotNull;
import ru.bell.autoleasing.payment.enums.PaymentStatus;
import ru.bell.autoleasing.payment.model.PaymentIdContractId;

import java.math.BigDecimal;

public record PaymentDto(
        @NotNull
        PaymentIdContractId paymentIdContractId,
        @NotNull
        BigDecimal amount,
        PaymentStatus status
) {}
