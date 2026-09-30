package com.example.invoiceservice.invoice;

import com.example.invoiceservice.batch.RunItemRepository;
import com.example.invoiceservice.batch.RunItemRepository.RunItem;
import com.example.invoiceservice.billing.Clients.BillingClient;
import com.example.invoiceservice.billing.Clients.Charge;
import com.example.invoiceservice.billing.Clients.DueSubscription;
import com.example.invoiceservice.billing.Clients.PaymentClient;
import com.example.invoiceservice.invoice.InvoiceRepository.Invoice;
import com.example.invoiceservice.invoice.InvoiceRepository.NewInvoice;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Invoices one subscription: issue the invoice, charge it, then move billing to the next period. Every step is
 * idempotent (unique invoice per period, charge keyed by invoice number, guarded period advance), so a crashed or
 * repeated run can simply be run again.
 */
@Service
public class InvoicingService {
    private static final Logger log = LoggerFactory.getLogger(InvoicingService.class);

    public record PreviewLine(String subscriptionId, String orderId, String customerId, String planName, int billingDay,
                              LocalDate periodStart, LocalDate periodEnd, boolean prorated, BigDecimal netAmount, BigDecimal vatAmount,
                              BigDecimal totalAmount, String existingInvoice) {}

    public record Preview(LocalDate runDate, int subscriptions, BigDecimal total, String currency, List<PreviewLine> lines) {}

    private final InvoiceRepository invoices;
    private final RunItemRepository runItems;
    private final BillingClient billing;
    private final PaymentClient payments;
    private final BigDecimal vatRate;
    private final String currency;

    public InvoicingService(InvoiceRepository invoices, RunItemRepository runItems, BillingClient billing, PaymentClient payments,
                            @Value("${invoice.vat-rate}") BigDecimal vatRate, @Value("${invoice.currency}") String currency) {
        this.invoices = invoices;
        this.runItems = runItems;
        this.billing = billing;
        this.payments = payments;
        this.vatRate = vatRate;
        this.currency = currency;
    }

    /** Processes one snapshotted subscription and records the outcome on its run item; never throws. */
    public void invoice(RunItem item) {
        try {
            InvoiceCalculator.Period period = InvoiceCalculator.period(item.periodStart(), item.billingDay());
            Invoice invoice = invoices.issue(new NewInvoice(item.runId(), item.subscriptionId(), item.orderId(), item.customerId(), item.planName(),
                period, InvoiceCalculator.amounts(item.monthlyAmount(), period, vatRate), vatRate, currency));
            if ("ISSUED".equals(invoice.status())) {
                Charge charge = payments.charge(invoice.invoiceNumber(), invoice.orderId(), invoice.customerId(), invoice.totalAmount(), invoice.currency());
                invoice = invoices.recordPayment(invoice.invoiceNumber(), charge.paid(), charge.id(), charge.transactionReference(), charge.statusReason());
            }
            // The period is owed whether or not the charge succeeded, so billing always moves on; failed charges are followed up separately.
            billing.recordBilledPeriod(invoice.orderId(), invoice.periodStart(), invoice.periodEnd());
            runItems.finish(item.id(), "INVOICED", invoice.invoiceNumber(), invoice.status() + " " + invoice.currency() + " " + invoice.totalAmount()
                + (invoice.failureReason() == null ? "" : " - " + invoice.failureReason()));
        } catch (RuntimeException failure) {
            log.warn("Invoicing failed for subscription {} (order {}) in run {}", item.subscriptionId(), item.orderId(), item.runId(), failure);
            runItems.finish(item.id(), "FAILED", null, failure.getMessage());
        }
    }

    /** What a run on {@code runDate} would invoice right now, without issuing, charging or changing anything. */
    public Preview preview(LocalDate runDate) {
        List<PreviewLine> lines = billing.due(runDate).stream().map(this::previewLine).toList();
        BigDecimal total = lines.stream().filter(line -> line.existingInvoice() == null).map(PreviewLine::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Preview(runDate, lines.size(), total, currency, lines);
    }

    private PreviewLine previewLine(DueSubscription subscription) {
        int day = InvoiceCalculator.billingDay(subscription.billingDay(), subscription.nextBillingDate());
        InvoiceCalculator.Period period = InvoiceCalculator.period(subscription.nextBillingDate(), day);
        InvoiceCalculator.Amounts amounts = InvoiceCalculator.amounts(subscription.monthlyAmount(), period, vatRate);
        String existing = invoices.findForPeriod(subscription.id(), period.start()).map(Invoice::invoiceNumber).orElse(null);
        return new PreviewLine(subscription.id(), subscription.orderId(), subscription.customerId(), subscription.planName(), day, period.start(),
            period.end(), amounts.prorated(), amounts.net(), amounts.vat(), amounts.total(), existing);
    }
}
