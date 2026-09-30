package com.example.invoiceservice.batch;

import com.example.invoiceservice.batch.RunItemRepository.Counts;
import com.example.invoiceservice.batch.RunItemRepository.RunItem;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Starts invoicing runs (scheduled or manual) and reports their history from Spring Batch's job repository. */
@Service
public class InvoicingRuns {
    public enum Trigger { SCHEDULED, MANUAL }

    public record Run(long runId, Long executionId, LocalDate runDate, String trigger, String status, String exitDescription,
                      Instant startedAt, Instant endedAt, Counts counts) {}

    private final JobOperator jobOperator;
    private final JobRepository jobRepository;
    private final Job job;
    private final RunItemRepository runItems;
    private final ZoneId zone;

    public InvoicingRuns(JobOperator jobOperator, JobRepository jobRepository, Job monthlyInvoicing, RunItemRepository runItems,
                         @Value("${invoice.zone}") String zone) {
        this.jobOperator = jobOperator;
        this.jobRepository = jobRepository;
        this.job = monthlyInvoicing;
        this.runItems = runItems;
        this.zone = ZoneId.of(zone);
    }

    public LocalDate today() { return LocalDate.now(zone); }

    /**
     * Starts a run. A scheduled run is identified by its date alone, so it happens at most once per day; a manual run
     * also carries its request time, so agents can run again the same day (already-invoiced subscriptions are no longer due).
     */
    public Run start(LocalDate runDate, Trigger trigger) {
        if (runDate.isAfter(today())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Runs cannot be dated in the future; use preview to plan ahead");
        if (!jobRepository.findRunningJobExecutions(InvoicingJobConfig.JOB_NAME).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An invoicing run is already in progress");
        }
        JobParametersBuilder parameters = new JobParametersBuilder().addLocalDate("runDate", runDate).addString("trigger", trigger.name());
        if (trigger == Trigger.MANUAL) parameters.addLong("requestedAt", System.currentTimeMillis());
        try {
            JobExecution execution = jobOperator.start(job, parameters.toJobParameters());
            return toRun(execution.getJobInstance(), execution);
        } catch (Exception refused) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The invoicing run could not start: " + refused.getMessage());
        }
    }

    /** True once the scheduled run for {@code runDate} has been attempted, whatever its outcome. */
    public boolean scheduledRunExists(LocalDate runDate) {
        JobParameters parameters = new JobParametersBuilder().addLocalDate("runDate", runDate).addString("trigger", Trigger.SCHEDULED.name()).toJobParameters();
        return jobRepository.getJobInstance(InvoicingJobConfig.JOB_NAME, parameters) != null;
    }

    public List<Run> recent(int limit) {
        List<JobInstance> instances = jobRepository.getJobInstances(InvoicingJobConfig.JOB_NAME, 0, limit);
        var counts = runItems.counts(instances.stream().map(JobInstance::getInstanceId).toList());
        return instances.stream().map(instance -> {
            JobExecution last = jobRepository.getLastJobExecution(instance);
            Run run = toRun(instance, last);
            return new Run(run.runId(), run.executionId(), run.runDate(), run.trigger(), run.status(), run.exitDescription(), run.startedAt(), run.endedAt(),
                counts.getOrDefault(instance.getInstanceId(), new Counts(0, 0, 0, 0)));
        }).toList();
    }

    public List<RunItem> items(long runId) { return runItems.forRun(runId); }

    private Run toRun(JobInstance instance, JobExecution execution) {
        JobParameters parameters = execution == null ? new JobParameters() : execution.getJobParameters();
        String exit = execution == null ? null : execution.getExitStatus().getExitDescription();
        return new Run(instance.getInstanceId(), execution == null ? null : execution.getId(), parameters.getLocalDate("runDate"),
            parameters.getString("trigger"), execution == null ? "UNKNOWN" : execution.getStatus().name(), exit == null || exit.isBlank() ? null : exit,
            instant(execution == null ? null : execution.getStartTime()), instant(execution == null ? null : execution.getEndTime()), null);
    }

    private Instant instant(LocalDateTime time) { return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant(); }
}
