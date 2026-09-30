package com.example.invoiceservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.invoiceservice.batch.InvoicingRuns;
import com.example.invoiceservice.billing.Clients.BillingClient;
import com.example.invoiceservice.billing.Clients.Charge;
import com.example.invoiceservice.billing.Clients.DueSubscription;
import com.example.invoiceservice.billing.Clients.PaymentClient;
import com.example.invoiceservice.invoice.InvoiceRepository;
import com.example.invoiceservice.invoice.InvoicingService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Runs the real batch job against an in-memory Postgres-compatible database with billing and payments mocked. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:invoicing;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "invoice.async-runs=false", "invoice.schedule-initial-delay-ms=3600000", "invoice.zone=UTC"})
class InvoicingJobTests {
    @MockitoBean BillingClient billing;
    @MockitoBean PaymentClient payments;
    @Autowired InvoicingRuns runs;
    @Autowired InvoicingService invoicing;
    @Autowired InvoiceRepository invoices;

    // A fixed past run date keeps the periods and amounts independent of when the test runs.
    private static final LocalDate RUN_DATE = LocalDate.of(2026, 3, 15);

    private static DueSubscription due(String id, String customer, Integer billingDay) {
        return new DueSubscription(id, "order-" + id, customer, "Unlimited", new BigDecimal("29.99"), billingDay, RUN_DATE.atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    void invoicesChargesAndAdvancesEachDueSubscriptionOnlyOnce() {
        LocalDate today = RUN_DATE;
        when(billing.due(any())).thenReturn(List.of(due("sub-1", "customer-1", null), due("sub-2", "customer-2", null), due("sub-3", "customer-3", null)));
        when(payments.charge(any(), eq("order-sub-1"), any(), any(), any())).thenAnswer(i -> new Charge("pay-1", "COMPLETED", "PAY-1", null));
        when(payments.charge(any(), eq("order-sub-2"), any(), any(), any())).thenAnswer(i -> new Charge("pay-2", "FAILED", "PAY-2", "Card declined (simulated)"));
        when(payments.charge(any(), eq("order-sub-3"), any(), any(), any())).thenThrow(new IllegalStateException("payment-service unavailable"));

        // Planning first: the preview shows all three and changes nothing.
        var preview = invoicing.preview(today);
        assertThat(preview.subscriptions()).isEqualTo(3);
        assertThat(preview.total()).isEqualByComparingTo("89.97");
        assertThat(invoices.search(null, null, null, null, 10)).isEmpty();

        var run = runs.start(today, InvoicingRuns.Trigger.MANUAL);
        assertThat(run.status()).isEqualTo("COMPLETED");
        var items = runs.items(run.runId());
        assertThat(items).extracting(i -> i.subscriptionId() + ":" + i.state())
            .containsExactlyInAnyOrder("sub-1:INVOICED", "sub-2:INVOICED", "sub-3:FAILED");

        assertThat(invoices.search("customer-1", null, null, null, 10)).singleElement().satisfies(invoice -> {
            assertThat(invoice.status()).isEqualTo("PAID");
            assertThat(invoice.totalAmount()).isEqualByComparingTo("29.99");
            assertThat(invoice.periodStart()).isEqualTo(LocalDate.of(2026, 3, 15));
            assertThat(invoice.periodEnd()).isEqualTo(LocalDate.of(2026, 4, 15));
            assertThat(invoice.invoiceNumber()).matches("INV-\\d{4}-\\d{6}");
        });
        assertThat(invoices.search("customer-2", null, null, null, 10)).singleElement().satisfies(invoice -> {
            assertThat(invoice.status()).isEqualTo("PAYMENT_FAILED");
            assertThat(invoice.failureReason()).isEqualTo("Card declined (simulated)");
        });
        verify(billing).recordBilledPeriod(eq("order-sub-1"), eq(today), any());
        verify(billing).recordBilledPeriod(eq("order-sub-2"), eq(today), any());
        verify(billing, never()).recordBilledPeriod(eq("order-sub-3"), any(), any());

        // A second run (billing not yet advanced in this mock) must not issue or charge anything twice.
        when(payments.charge(any(), eq("order-sub-3"), any(), any(), any())).thenAnswer(i -> new Charge("pay-3", "COMPLETED", "PAY-3", null));
        runs.start(today, InvoicingRuns.Trigger.MANUAL);
        verify(payments, times(1)).charge(any(), eq("order-sub-1"), any(), any(), any());
        verify(payments, times(1)).charge(any(), eq("order-sub-2"), any(), any(), any());
        assertThat(invoices.search(null, null, null, null, 10)).hasSize(3);
        assertThat(invoices.search("customer-3", null, null, null, 10)).singleElement().extracting(InvoiceRepository.Invoice::status).isEqualTo("PAID");
        assertThat(invoicing.preview(today).lines()).allSatisfy(line -> assertThat(line.existingInvoice()).isNotNull());
    }
}
