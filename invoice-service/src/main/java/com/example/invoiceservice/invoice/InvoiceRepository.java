package com.example.invoiceservice.invoice;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class InvoiceRepository {
    public record Invoice(long id, String invoiceNumber, long runId, String subscriptionId, String orderId, String customerId, String planName,
                          LocalDate periodStart, LocalDate periodEnd, boolean prorated, BigDecimal netAmount, BigDecimal vatRate,
                          BigDecimal vatAmount, BigDecimal totalAmount, String currency, String status, String paymentId,
                          String paymentReference, String failureReason, Instant issuedAt, Instant updatedAt) {}

    public record NewInvoice(long runId, String subscriptionId, String orderId, String customerId, String planName,
                             InvoiceCalculator.Period period, InvoiceCalculator.Amounts amounts, BigDecimal vatRate, String currency) {}

    private static final String COLUMNS = "id, invoice_number, run_id, subscription_id, order_id, customer_id, plan_name, period_start, period_end, prorated, "
        + "net_amount, vat_rate, vat_amount, total_amount, currency, status, payment_id, payment_reference, failure_reason, issued_at, updated_at";
    private final JdbcClient jdbc;
    private final ZoneId zone;

    public InvoiceRepository(JdbcClient jdbc, @org.springframework.beans.factory.annotation.Value("${invoice.zone}") String zone) {
        this.jdbc = jdbc;
        this.zone = ZoneId.of(zone);
    }

    public Optional<Invoice> findForPeriod(String subscriptionId, LocalDate periodStart) {
        return jdbc.sql("select " + COLUMNS + " from invoices where subscription_id = ? and period_start = ?")
            .params(subscriptionId, periodStart).query(InvoiceRepository::map).optional();
    }

    public Optional<Invoice> findByNumber(String invoiceNumber) {
        return jdbc.sql("select " + COLUMNS + " from invoices where invoice_number = ?").param(invoiceNumber).query(InvoiceRepository::map).optional();
    }

    /** Issues the invoice for a period, or returns the one a previous (possibly concurrent) attempt already issued. */
    public Invoice issue(NewInvoice invoice) {
        Optional<Invoice> existing = findForPeriod(invoice.subscriptionId(), invoice.period().start());
        if (existing.isPresent()) return existing.get();
        long sequence = jdbc.sql("select nextval('invoice_number_seq')").query(Long.class).single();
        String number = "INV-%d-%06d".formatted(Year.now(zone).getValue(), sequence);
        Timestamp now = Timestamp.from(Instant.now());
        try {
            jdbc.sql("insert into invoices (invoice_number, run_id, subscription_id, order_id, customer_id, plan_name, period_start, period_end, prorated, "
                    + "net_amount, vat_rate, vat_amount, total_amount, currency, status, issued_at, updated_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,'ISSUED',?,?)")
                .params(number, invoice.runId(), invoice.subscriptionId(), invoice.orderId(), invoice.customerId(), invoice.planName(),
                    invoice.period().start(), invoice.period().end(), invoice.amounts().prorated(), invoice.amounts().net(), invoice.vatRate(),
                    invoice.amounts().vat(), invoice.amounts().total(), invoice.currency(), now, now)
                .update();
        } catch (DuplicateKeyException concurrent) {
            return findForPeriod(invoice.subscriptionId(), invoice.period().start()).orElseThrow(() -> concurrent);
        }
        return findByNumber(number).orElseThrow();
    }

    public Invoice recordPayment(String invoiceNumber, boolean paid, String paymentId, String reference, String failureReason) {
        jdbc.sql("update invoices set status = ?, payment_id = ?, payment_reference = ?, failure_reason = ?, updated_at = ? where invoice_number = ?")
            .params(paid ? "PAID" : "PAYMENT_FAILED", paymentId, reference, paid ? null : failureReason, Timestamp.from(Instant.now()), invoiceNumber)
            .update();
        return findByNumber(invoiceNumber).orElseThrow();
    }

    public List<Invoice> search(String customerId, String orderId, Long runId, String status, int limit) {
        StringBuilder sql = new StringBuilder("select " + COLUMNS + " from invoices where 1 = 1");
        List<Object> params = new java.util.ArrayList<>();
        if (customerId != null && !customerId.isBlank()) { sql.append(" and customer_id = ?"); params.add(customerId.trim()); }
        if (orderId != null && !orderId.isBlank()) { sql.append(" and order_id = ?"); params.add(orderId.trim()); }
        if (runId != null) { sql.append(" and run_id = ?"); params.add(runId); }
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); params.add(status.trim().toUpperCase()); }
        sql.append(" order by issued_at desc, id desc limit ?");
        params.add(Math.min(Math.max(limit, 1), 500));
        return jdbc.sql(sql.toString()).params(params).query(InvoiceRepository::map).list();
    }

    private static Invoice map(ResultSet rs, int row) throws SQLException {
        return new Invoice(rs.getLong("id"), rs.getString("invoice_number"), rs.getLong("run_id"), rs.getString("subscription_id"),
            rs.getString("order_id"), rs.getString("customer_id"), rs.getString("plan_name"), rs.getObject("period_start", LocalDate.class),
            rs.getObject("period_end", LocalDate.class), rs.getBoolean("prorated"), rs.getBigDecimal("net_amount"), rs.getBigDecimal("vat_rate"),
            rs.getBigDecimal("vat_amount"), rs.getBigDecimal("total_amount"), rs.getString("currency"), rs.getString("status"),
            rs.getString("payment_id"), rs.getString("payment_reference"), rs.getString("failure_reason"),
            rs.getTimestamp("issued_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
}
