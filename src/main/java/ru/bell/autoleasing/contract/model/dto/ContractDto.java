package ru.bell.autoleasing.contract.model.dto;

import jakarta.validation.constraints.NotNull;
import ru.bell.autoleasing.car.model.Car;
import ru.bell.autoleasing.client.model.Client;
import ru.bell.autoleasing.contract.enums.ContractStatus;
import ru.bell.autoleasing.payment.model.Payment;

import java.math.BigDecimal;
import java.util.List;

public record ContractDto(
        Long id,
        List<Payment> payments,
        @NotNull
        Car car,
        @NotNull
        Client client,
        @NotNull
        Integer durationInMonths,
        @NotNull
        BigDecimal downPayment,
        @NotNull
        BigDecimal interestRate,
        ContractStatus status
) {
    public List<Payment> payments() {
        if (payments == null) {
            return List.of();
        }
        return payments;
    }
}
