package ru.bell.autoleasing.schedule;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ScheduledPayment(int number, LocalDate dueDate, BigDecimal amount, BigDecimal principalPart,
                               BigDecimal markup, BigDecimal vat) {
}
