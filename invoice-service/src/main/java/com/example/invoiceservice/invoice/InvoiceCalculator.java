package com.example.invoiceservice.invoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Monthly billing arithmetic. Subscriptions are billed in advance: an invoice issued on {@code periodStart} covers
 * [periodStart, periodEnd), where periodEnd is the next occurrence of the subscription's billing day. A full calendar
 * month costs the plan price; a shorter period (after the billing day changes) is prorated by day.
 */
public final class InvoiceCalculator {
    private InvoiceCalculator() {}

    public record Period(LocalDate start, LocalDate end) {
        public boolean fullMonth() { return end.equals(start.plusMonths(1)); }
        public long days() { return ChronoUnit.DAYS.between(start, end); }
    }

    /** Amounts are VAT-inclusive plan prices split into net and VAT. */
    public record Amounts(BigDecimal net, BigDecimal vat, BigDecimal total, boolean prorated) {}

    /** Subscriptions created before billing days existed are billed on their current billing date's day (max 28). */
    public static int billingDay(Integer configured, LocalDate nextBillingDate) {
        return configured != null ? configured : Math.min(nextBillingDate.getDayOfMonth(), 28);
    }

    public static Period period(LocalDate start, int billingDay) {
        if (billingDay < 1 || billingDay > 28) throw new IllegalArgumentException("Billing day must be 1-28");
        LocalDate end = start.withDayOfMonth(billingDay);
        if (!end.isAfter(start)) end = end.plusMonths(1);
        return new Period(start, end);
    }

    public static Amounts amounts(BigDecimal monthlyPrice, Period period, BigDecimal vatRate) {
        BigDecimal total = period.fullMonth() ? monthlyPrice.setScale(2, RoundingMode.HALF_UP)
            : monthlyPrice.multiply(BigDecimal.valueOf(period.days()))
                .divide(BigDecimal.valueOf(period.start().lengthOfMonth()), 2, RoundingMode.HALF_UP);
        BigDecimal net = total.divide(BigDecimal.ONE.add(vatRate), 2, RoundingMode.HALF_UP);
        return new Amounts(net, total.subtract(net), total, !period.fullMonth());
    }
}
