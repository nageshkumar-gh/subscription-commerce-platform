package com.example.invoiceservice.batch;

import com.example.invoiceservice.batch.RunItemRepository.RunItem;
import com.example.invoiceservice.billing.Clients.BillingClient;
import com.example.invoiceservice.invoice.InvoiceCalculator;
import com.example.invoiceservice.invoice.InvoicingService;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The daily invoicing job, identified by the run date. Step 1 snapshots every subscription due on that date into
 * billing_run_items; step 2 invoices, charges and advances each snapshotted subscription in chunks.
 */
@Configuration
public class InvoicingJobConfig {
    public static final String JOB_NAME = "monthlyInvoicing";

    @Bean
    Job monthlyInvoicing(JobRepository jobRepository, Step snapshotDueSubscriptions, Step invoiceSubscriptions) {
        return new JobBuilder(JOB_NAME, jobRepository).start(snapshotDueSubscriptions).next(invoiceSubscriptions).build();
    }

    @Bean
    Step snapshotDueSubscriptions(JobRepository jobRepository, PlatformTransactionManager transactionManager, BillingClient billing, RunItemRepository runItems) {
        Tasklet snapshot = (contribution, chunkContext) -> {
            var stepExecution = chunkContext.getStepContext().getStepExecution();
            long runId = stepExecution.getJobExecution().getJobInstance().getInstanceId();
            LocalDate runDate = stepExecution.getJobParameters().getLocalDate("runDate");
            var due = billing.due(runDate);
            due.forEach(subscription -> runItems.snapshot(runId, subscription, InvoiceCalculator.billingDay(subscription.billingDay(), subscription.nextBillingDate())));
            contribution.incrementWriteCount(due.size());
            return RepeatStatus.FINISHED;
        };
        return new StepBuilder("snapshotDueSubscriptions", jobRepository).tasklet(snapshot, transactionManager).build();
    }

    @Bean
    Step invoiceSubscriptions(JobRepository jobRepository, PlatformTransactionManager transactionManager, ItemReader<RunItem> pendingRunItems,
                              InvoicingService invoicing, @Value("${invoice.chunk-size}") int chunkSize) {
        ItemWriter<RunItem> writer = chunk -> chunk.forEach(invoicing::invoice);
        return new StepBuilder("invoiceSubscriptions", jobRepository).<RunItem, RunItem>chunk(chunkSize)
            .reader(pendingRunItems).writer(writer).transactionManager(transactionManager).build();
    }

    /** Reads the run's PENDING items page by page (keyset on id), so a restarted run continues with what is left. */
    @Bean
    @StepScope
    ItemReader<RunItem> pendingRunItems(RunItemRepository runItems, @Value("#{stepExecution.jobExecution.jobInstance.instanceId}") Long runId,
                                        @Value("${invoice.chunk-size}") int pageSize) {
        return new ItemReader<>() {
            private final Deque<RunItem> page = new ArrayDeque<>();
            private long lastId;

            @Override
            public RunItem read() {
                if (page.isEmpty()) {
                    page.addAll(runItems.pending(runId, lastId, pageSize));
                    if (page.isEmpty()) return null;
                }
                RunItem next = page.poll();
                lastId = next.id();
                return next;
            }
        };
    }
}
