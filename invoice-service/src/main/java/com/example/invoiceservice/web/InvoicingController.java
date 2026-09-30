package com.example.invoiceservice.web;

import com.example.invoiceservice.batch.InvoicingRuns;
import com.example.invoiceservice.batch.RunItemRepository.RunItem;
import com.example.invoiceservice.invoice.InvoiceRepository;
import com.example.invoiceservice.invoice.InvoiceRepository.Invoice;
import com.example.invoiceservice.invoice.InvoicingService;
import com.example.invoiceservice.schedule.BillingSchedule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
@Tag(name = "Invoicing")
public class InvoicingController {
    public record StartRunRequest(LocalDate runDate) {}
    public record ScheduleRequest(@NotNull Boolean enabled, @NotNull @DateTimeFormat(pattern = "HH:mm") LocalTime runTime) {}

    private final InvoicingRuns runs;
    private final InvoicingService invoicing;
    private final InvoiceRepository invoices;
    private final BillingSchedule schedule;

    public InvoicingController(InvoicingRuns runs, InvoicingService invoicing, InvoiceRepository invoices, BillingSchedule schedule) {
        this.runs = runs;
        this.invoicing = invoicing;
        this.invoices = invoices;
        this.schedule = schedule;
    }

    @GetMapping("/billing-runs")
    @Operation(summary = "Recent invoicing runs with per-run counts")
    public List<InvoicingRuns.Run> recentRuns(@RequestParam(defaultValue = "30") int limit) { return runs.recent(Math.min(Math.max(limit, 1), 200)); }

    @PostMapping("/billing-runs")
    @Operation(summary = "Run invoicing now", description = "Invoices every subscription due on runDate (default today). Safe to repeat: nothing is invoiced or charged twice.")
    public ResponseEntity<InvoicingRuns.Run> start(@RequestBody(required = false) StartRunRequest request) {
        LocalDate runDate = request == null || request.runDate() == null ? runs.today() : request.runDate();
        return ResponseEntity.accepted().body(runs.start(runDate, InvoicingRuns.Trigger.MANUAL));
    }

    @GetMapping("/billing-runs/preview")
    @Operation(summary = "Plan a run", description = "Dry run: who would be invoiced on runDate (default today) and for how much. Nothing is issued or charged.")
    public InvoicingService.Preview preview(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate runDate) {
        return invoicing.preview(runDate == null ? runs.today() : runDate);
    }

    @GetMapping("/billing-runs/{runId}/items")
    @Operation(summary = "Every subscription a run picked up and its outcome")
    public List<RunItem> items(@PathVariable long runId) { return runs.items(runId); }

    @GetMapping("/billing-schedule")
    @Operation(summary = "Daily invoicing schedule")
    public BillingSchedule.Settings schedule() { return schedule.get(); }

    @PutMapping("/billing-schedule")
    @Operation(summary = "Change the daily run time or pause/resume the schedule")
    public BillingSchedule.Settings updateSchedule(@Valid @RequestBody ScheduleRequest request) { return schedule.update(request.enabled(), request.runTime()); }

    @GetMapping("/invoices")
    @Operation(summary = "Search invoices by customer, order, run or status")
    public List<Invoice> invoices(@RequestParam(required = false) String customerId, @RequestParam(required = false) String orderId,
                                  @RequestParam(required = false) Long runId, @RequestParam(required = false) String status,
                                  @RequestParam(defaultValue = "100") int limit) {
        return invoices.search(customerId, orderId, runId, status, limit);
    }

    @GetMapping("/invoices/{invoiceNumber}")
    @Operation(summary = "Get one invoice")
    public Invoice invoice(@PathVariable String invoiceNumber) {
        return invoices.findByNumber(invoiceNumber).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, Object>> refused(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("status", e.getStatusCode().value(), "message", String.valueOf(e.getReason())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream().findFirst().map(error -> error.getField() + ": " + error.getDefaultMessage()).orElse("Request is invalid");
        return ResponseEntity.badRequest().body(Map.of("status", 400, "message", message));
    }
}
