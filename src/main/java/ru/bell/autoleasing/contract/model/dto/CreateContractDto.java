package ru.bell.autoleasing.contract.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.bell.autoleasing.contract.enums.ContractStatus;
import ru.bell.autoleasing.payment.model.Payment;

import java.math.BigDecimal;
import java.util.List;

public record CreateContractDto(
        Long id,
        @NotBlank
        String vin,
        @NotBlank
        String client,
        @NotNull
        @DecimalMin("1")
        Integer durationInMonths,
        @NotNull
        @DecimalMin("0")
        BigDecimal downPayment,
        @NotNull
        @DecimalMin("0")
        BigDecimal interestRate,
        List<Payment> payments,
        ContractStatus status
) {
    public List<Payment> payments() {
        if (payments == null) {
            return List.of();
        }
        return payments;
    }
}
