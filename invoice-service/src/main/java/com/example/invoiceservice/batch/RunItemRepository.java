package com.example.invoiceservice.batch;

import com.example.invoiceservice.billing.Clients.DueSubscription;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The per-run record of every subscription an invoicing run picked up, and what happened to it. */
@Repository
public class RunItemRepository {
    public record RunItem(long id, long runId, String subscriptionId, String orderId, String customerId, String planName,
                          BigDecimal monthlyAmount, int billingDay, LocalDate periodStart, String state, String invoiceNumber,
                          String message, Instant updatedAt) {}

    public record Counts(long total, long pending, long invoiced, long failed) {}

    private final JdbcClient jdbc;

    public RunItemRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    /** Records a due subscription for the run; a repeat (step restarted after a crash) is ignored. */
    public void snapshot(long runId, DueSubscription subscription, int billingDay) {
        try {
            jdbc.sql("insert into billing_run_items (run_id, subscription_id, order_id, customer_id, plan_name, monthly_amount, billing_day, period_start, state, updated_at) "
                    + "values (?,?,?,?,?,?,?,?,'PENDING',?)")
                .params(runId, subscription.id(), subscription.orderId(), subscription.customerId(), subscription.planName(), subscription.monthlyAmount(),
                    billingDay, subscription.nextBillingDate(), Timestamp.from(Instant.now()))
                .update();
        } catch (DuplicateKeyException alreadySnapshotted) {
            // unique (run_id, subscription_id)
        }
    }

    /** Next page of items still to invoice, in id order so a restarted step resumes where it stopped. */
    public List<RunItem> pending(long runId, long afterId, int limit) {
        return jdbc.sql("select * from billing_run_items where run_id = ? and state = 'PENDING' and id > ? order by id limit ?")
            .params(runId, afterId, limit).query(RunItemRepository::map).list();
    }

    public List<RunItem> forRun(long runId) {
        return jdbc.sql("select * from billing_run_items where run_id = ? order by id").param(runId).query(RunItemRepository::map).list();
    }

    public void finish(long id, String state, String invoiceNumber, String message) {
        jdbc.sql("update billing_run_items set state = ?, invoice_number = ?, message = ?, updated_at = ? where id = ?")
            .params(state, invoiceNumber, message == null ? null : message.substring(0, Math.min(message.length(), 1000)), Timestamp.from(Instant.now()), id)
            .update();
    }

    public Map<Long, Counts> counts(List<Long> runIds) {
        if (runIds.isEmpty()) return Map.of();
        return jdbc.sql("select run_id, count(*) total, sum(case when state = 'PENDING' then 1 else 0 end) pending, "
                + "sum(case when state = 'INVOICED' then 1 else 0 end) invoiced, sum(case when state = 'FAILED' then 1 else 0 end) failed "
                + "from billing_run_items where run_id in (:ids) group by run_id")
            .param("ids", runIds)
            .query((rs, row) -> Map.entry(rs.getLong("run_id"), new Counts(rs.getLong("total"), rs.getLong("pending"), rs.getLong("invoiced"), rs.getLong("failed"))))
            .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static RunItem map(ResultSet rs, int row) throws SQLException {
        return new RunItem(rs.getLong("id"), rs.getLong("run_id"), rs.getString("subscription_id"), rs.getString("order_id"), rs.getString("customer_id"),
            rs.getString("plan_name"), rs.getBigDecimal("monthly_amount"), rs.getInt("billing_day"), rs.getObject("period_start", LocalDate.class),
            rs.getString("state"), rs.getString("invoice_number"), rs.getString("message"), rs.getTimestamp("updated_at").toInstant());
    }
}
