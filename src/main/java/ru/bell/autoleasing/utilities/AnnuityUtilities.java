package ru.bell.autoleasing.utilities;

import ru.bell.autoleasing.car.model.Car;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class AnnuityUtilities {
    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);
    private static final int MONEY_SCALE = 2;

    public static BigDecimal calculateMonthlyAnnuity(BigDecimal creditAmount,
                                                     BigDecimal annualInterestPercent,
                                                     int durationInMonths) {
        if (durationInMonths <= 0) {
            throw new IllegalArgumentException("срок (мес.) должен быть > 0");
        }

        BigDecimal monthlyRate = annualInterestPercent
                .divide(BigDecimal.valueOf(100), MC)
                .divide(BigDecimal.valueOf(12), MC);

        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            return creditAmount
                    .divide(BigDecimal.valueOf(durationInMonths), MONEY_SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
        BigDecimal pow = onePlusR.pow(durationInMonths, MC);

        BigDecimal numerator = monthlyRate.multiply(pow, MC);
        BigDecimal denominator = pow.subtract(BigDecimal.ONE, MC);

        BigDecimal annuityFactor = numerator.divide(denominator, MC);

        return creditAmount.multiply(annuityFactor, MC)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal calculateMonthlyAnnuityWithCar(Car car,
                                                            BigDecimal downPayment,
                                                            BigDecimal annualInterestPercent,
                                                            int durationInMonths) {
        BigDecimal creditAmount = getCreditAmount(car, downPayment);
        return calculateMonthlyAnnuity(creditAmount, annualInterestPercent, durationInMonths);
    }

    public static BigDecimal getCreditAmount(Car car, BigDecimal downPayment) {
        return car.getPrice().subtract(downPayment);
    }
}