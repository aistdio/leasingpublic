package ru.bell.autoleasing.schedule;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentScheduleCalculatorTest {

    private static BigDecimal sum(List<ScheduledPayment> schedule) {
        return schedule.stream().map(ScheduledPayment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void firstAndLastPaymentOfTwelveMonths() {
        List<ScheduledPayment> s = PaymentScheduleCalculator.calculate(
                new BigDecimal("1200000.00"), new BigDecimal("12"), 12, LocalDate.of(2024, 1, 31));

        assertThat(s).hasSize(12);
        ScheduledPayment first = s.get(0);
        assertThat(first.amount()).isEqualByComparingTo("106618.55");
        assertThat(first.principalPart()).isEqualByComparingTo("78848.79");
        assertThat(first.markup()).isEqualByComparingTo("10000.00");
        assertThat(first.vat()).isEqualByComparingTo("17769.76");
        ScheduledPayment last = s.get(11);
        assertThat(last.amount()).isEqualByComparingTo("106618.51");
        assertThat(last.principalPart()).isEqualByComparingTo("87969.07");
        assertThat(last.markup()).isEqualByComparingTo("879.69");
        assertThat(last.vat()).isEqualByComparingTo("17769.75");
        assertThat(sum(s)).isEqualByComparingTo("1279422.56");
    }

    @Test
    void dueDatesAreCountedFromStartAndClampedToMonthEnd() {
        List<ScheduledPayment> s = PaymentScheduleCalculator.calculate(
                new BigDecimal("1200000.00"), new BigDecimal("12"), 12, LocalDate.of(2024, 1, 31));

        assertThat(s.get(0).dueDate()).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(s.get(1).dueDate()).isEqualTo(LocalDate.of(2024, 3, 31));
        assertThat(s.get(2).dueDate()).isEqualTo(LocalDate.of(2024, 4, 30));
        assertThat(s.get(11).dueDate()).isEqualTo(LocalDate.of(2025, 1, 31));
    }

    @Test
    void componentsAddUpToAmountInEveryPayment() {
        List<ScheduledPayment> s = PaymentScheduleCalculator.calculate(
                new BigDecimal("1000000.00"), new BigDecimal("10.5"), 7, LocalDate.of(2025, 5, 15));

        assertThat(s.get(0).amount()).isEqualByComparingTo("147900.70");
        assertThat(s.get(0).principalPart()).isEqualByComparingTo("115958.91");
        assertThat(s.get(0).markup()).isEqualByComparingTo("7291.67");
        assertThat(s.get(0).vat()).isEqualByComparingTo("24650.12");
        assertThat(s.get(6).principalPart()).isEqualByComparingTo("122181.49");
        assertThat(sum(s)).isEqualByComparingTo("1035304.90");
        s.forEach(p -> assertThat(p.principalPart().add(p.markup()).add(p.vat())).isEqualByComparingTo(p.amount()));
    }

    @Test
    void zeroRateHasNoMarkup() {
        List<ScheduledPayment> s = PaymentScheduleCalculator.calculate(
                new BigDecimal("120000.00"), BigDecimal.ZERO, 12, LocalDate.of(2025, 1, 1));

        s.forEach(p -> {
            assertThat(p.amount()).isEqualByComparingTo("10000.00");
            assertThat(p.markup()).isEqualByComparingTo("0.00");
            assertThat(p.vat()).isEqualByComparingTo("1666.67");
            assertThat(p.principalPart()).isEqualByComparingTo("8333.33");
        });
    }

    @Test
    void rejectsNonPositiveFinancedAmount() {
        assertThatThrownBy(() -> PaymentScheduleCalculator.calculate(BigDecimal.ZERO, BigDecimal.TEN, 12, LocalDate.of(2025, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
