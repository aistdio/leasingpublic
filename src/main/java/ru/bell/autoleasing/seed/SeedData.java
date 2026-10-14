package ru.bell.autoleasing.seed;

import ru.bell.autoleasing.car.enums.CarStatus;
import ru.bell.autoleasing.client.enums.ClientType;
import ru.bell.autoleasing.contract.enums.ContractStatus;
import ru.bell.autoleasing.payment.enums.PaymentStatus;
import ru.bell.autoleasing.user.UserRole;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SeedData(List<CarSeed> cars, List<ClientSeed> clients, List<ContractSeed> contracts,
                       List<PaymentSeed> payments, List<UserSeed> users) {

    public record CarSeed(String vin, String brand, String model, int releaseYear, BigDecimal price, CarStatus status) {
    }

    public record ClientSeed(long id, String name, String passport, String phone, ClientType type, String inn,
                             String legalName) {
    }

    public record ContractSeed(long id, String number, String carVin, long clientId, int durationInMonths,
                               BigDecimal downPayment, BigDecimal interestRate, BigDecimal monthlyPayment,
                               ContractStatus status, LocalDate startDate) {
    }

    public record PaymentSeed(long contractId, long paymentId, LocalDate dueDate, LocalDate paidDate, BigDecimal amount,
                              BigDecimal principalPart, BigDecimal markup, BigDecimal vat, BigDecimal penalty,
                              PaymentStatus status) {
    }

    public record UserSeed(String username, UserRole role, Long clientId) {
    }
}
