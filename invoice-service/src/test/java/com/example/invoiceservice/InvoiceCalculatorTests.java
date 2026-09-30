package com.example.invoiceservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.invoiceservice.invoice.InvoiceCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InvoiceCalculatorTests {
    private static final BigDecimal VAT = new BigDecimal("0.23");

    @Test
    void billsAFullMonthOnTheBillingDay() {
        var period = InvoiceCalculator.period(LocalDate.of(2026, 10, 15), 15);
        assertThat(period.end()).isEqualTo(LocalDate.of(2026, 11, 15));
        var amounts = InvoiceCalculator.amounts(new BigDecimal("29.99"), period, VAT);
        assertThat(amounts.prorated()).isFalse();
        assertThat(amounts.total()).isEqualByComparingTo("29.99");
        assertThat(amounts.net()).isEqualByComparingTo("24.38");
        assertThat(amounts.vat()).isEqualByComparingTo("5.61");
        assertThat(amounts.net().add(amounts.vat())).isEqualByComparingTo(amounts.total());
    }

    @Test
    void proratesUpToANewBillingDay() {
        // Moved from the 15th to the 1st: 17 days of October (31 days) are billed, then full months from 1 November.
        var period = InvoiceCalculator.period(LocalDate.of(2026, 10, 15), 1);
        assertThat(period.end()).isEqualTo(LocalDate.of(2026, 11, 1));
        var amounts = InvoiceCalculator.amounts(new BigDecimal("29.99"), period, VAT);
        assertThat(amounts.prorated()).isTrue();
        assertThat(amounts.total()).isEqualByComparingTo("16.45");
    }

    @Test
    void handlesMonthEndBillingDatesFromOlderSubscriptions() {
        // A subscription started on the 31st is billed on the 28th (the latest day every month has).
        assertThat(InvoiceCalculator.billingDay(null, LocalDate.of(2026, 1, 31))).isEqualTo(28);
        var period = InvoiceCalculator.period(LocalDate.of(2026, 1, 31), 28);
        assertThat(period.end()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(InvoiceCalculator.billingDay(12, LocalDate.of(2026, 1, 31))).isEqualTo(12);
    }

    @Test
    void rejectsBillingDaysSomeMonthsDoNotHave() {
        assertThatThrownBy(() -> InvoiceCalculator.period(LocalDate.of(2026, 1, 1), 31)).isInstanceOf(IllegalArgumentException.class);
    }
}
