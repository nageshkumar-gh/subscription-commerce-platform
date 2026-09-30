package com.example.invoiceservice.schedule;

import com.example.invoiceservice.batch.InvoicingRuns;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * The daily invoicing schedule, editable at runtime and stored in Postgres. Checked every minute: once today's run time
 * has passed and today's scheduled run has not happened, it starts. Missed runs (service was down) catch up on the next check.
 */
@Service
public class BillingSchedule {
    private static final Logger log = LoggerFactory.getLogger(BillingSchedule.class);

    public record Settings(boolean enabled, LocalTime runTime, String zone, Instant nextRunAt, Instant updatedAt) {}

    private final JdbcClient jdbc;
    private final InvoicingRuns runs;
    private final ZoneId zone;

    public BillingSchedule(JdbcClient jdbc, InvoicingRuns runs, @Value("${invoice.zone}") String zone) {
        this.jdbc = jdbc;
        this.runs = runs;
        this.zone = ZoneId.of(zone);
    }

    public Settings get() {
        return jdbc.sql("select enabled, run_time, updated_at from billing_schedule where id = 1").query((rs, row) -> {
            boolean enabled = rs.getBoolean("enabled");
            LocalTime runTime = rs.getObject("run_time", LocalTime.class);
            return new Settings(enabled, runTime, zone.getId(), enabled ? nextRun(runTime) : null, rs.getTimestamp("updated_at").toInstant());
        }).single();
    }

    public Settings update(boolean enabled, LocalTime runTime) {
        jdbc.sql("update billing_schedule set enabled = ?, run_time = ?, updated_at = ? where id = 1")
            .params(enabled, runTime.withSecond(0).withNano(0), Timestamp.from(Instant.now())).update();
        return get();
    }

    @Scheduled(initialDelayString = "${invoice.schedule-initial-delay-ms:30000}", fixedDelayString = "${invoice.schedule-check-ms:60000}")
    public void tick() {
        Settings settings = get();
        ZonedDateTime now = ZonedDateTime.now(zone);
        if (!settings.enabled() || now.toLocalTime().isBefore(settings.runTime()) || runs.scheduledRunExists(now.toLocalDate())) return;
        try {
            runs.start(now.toLocalDate(), InvoicingRuns.Trigger.SCHEDULED);
            log.info("Started scheduled invoicing run for {}", now.toLocalDate());
        } catch (RuntimeException notStarted) {
            log.warn("Scheduled invoicing run for {} did not start: {}", now.toLocalDate(), notStarted.getMessage());
        }
    }

    private Instant nextRun(LocalTime runTime) {
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime today = now.toLocalDate().atTime(runTime).atZone(zone);
        if (runs.scheduledRunExists(now.toLocalDate())) return today.plusDays(1).toInstant();
        // Not yet run today: either later today, or overdue and starting on the next check.
        return (now.isBefore(today) ? today : now).toInstant();
    }
}
