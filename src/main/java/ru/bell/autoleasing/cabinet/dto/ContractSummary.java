package ru.bell.autoleasing.cabinet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContractSummary(long contractId, String contractNumber, String status, String statusText,
                              LocalDate startDate, int durationInMonths, BigDecimal monthlyPayment, String car,
                              String vin, BigDecimal remainingDebt, int unpaidCount, LocalDate nextDueDate) {
}
