package ru.bell.autoleasing.cabinet.dto;

import java.math.BigDecimal;
import java.util.List;

public record UpcomingPayments(PaymentRow nextPayment, List<PaymentRow> upcoming, int overdueCount,
                               BigDecimal overdueTotal) {
}
