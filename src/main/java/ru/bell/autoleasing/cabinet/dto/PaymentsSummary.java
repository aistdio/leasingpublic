package ru.bell.autoleasing.cabinet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentsSummary(LocalDate periodFrom, LocalDate periodTo, BigDecimal paidTotal, int paidCount,
                              BigDecimal accruedTotal, int accruedCount, String currency) {
}
