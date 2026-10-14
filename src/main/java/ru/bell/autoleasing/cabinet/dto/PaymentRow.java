package ru.bell.autoleasing.cabinet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentRow(long contractId, String contractNumber, long paymentId, LocalDate dueDate,
                         LocalDate paidDate, BigDecimal amount, BigDecimal principalPart, BigDecimal markup,
                         BigDecimal vat, BigDecimal penalty, String status, String statusText, boolean overdue) {
}
