package ru.bell.autoleasing.schedule;

import ru.bell.autoleasing.utilities.AnnuityUtilities;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class PaymentScheduleCalculator {
    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);
    private static final BigDecimal VAT_PERCENT = BigDecimal.valueOf(20);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal HUNDRED_TWENTY = BigDecimal.valueOf(120);
    private static final BigDecimal TWELVE_HUNDRED = BigDecimal.valueOf(1200);

    private PaymentScheduleCalculator() {
    }

    public static List<ScheduledPayment> calculate(BigDecimal financedAmount, BigDecimal annualRatePercent,
                                                   int months, LocalDate startDate) {
        if (financedAmount == null || financedAmount.signum() <= 0) {
            throw new IllegalArgumentException("сумма финансирования должна быть > 0");
        }
        BigDecimal annuity = AnnuityUtilities.calculateMonthlyAnnuity(financedAmount, annualRatePercent, months);
        BigDecimal monthlyRate = annualRatePercent.divide(TWELVE_HUNDRED, MC);
        BigDecimal balance = financedAmount;
        List<ScheduledPayment> result = new ArrayList<>(months);
        for (int number = 1; number <= months; number++) {
            BigDecimal interest = balance.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal principalWithVat = annuity.subtract(interest);
            BigDecimal amount = annuity;
            if (number == months) {
                principalWithVat = balance;
                amount = principalWithVat.add(interest);
            }
            balance = balance.subtract(principalWithVat);
            BigDecimal vat = amount.multiply(VAT_PERCENT).divide(HUNDRED_TWENTY, 2, RoundingMode.HALF_UP);
            BigDecimal markup = interest.multiply(HUNDRED).divide(HUNDRED_TWENTY, 2, RoundingMode.HALF_UP);
            BigDecimal principalPart = amount.subtract(vat).subtract(markup);
            result.add(new ScheduledPayment(number, startDate.plusMonths(number), amount, principalPart, markup, vat));
        }
        return result;
    }
}
