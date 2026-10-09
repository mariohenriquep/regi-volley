package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.usecase.ListSubscriptionsByPaymentStatusUseCase;
import com.regivolley.api.application.usecase.MarkSubscriptionOverdueUseCase;
import com.regivolley.api.application.usecase.RecordPaymentUseCase;
import com.regivolley.api.infrastructure.security.AuthenticatedActor;
import com.regivolley.api.infrastructure.security.CurrentActor;
import com.regivolley.api.infrastructure.web.dto.PaymentRecordedResponse;
import com.regivolley.api.infrastructure.web.dto.PlausibleDate;
import com.regivolley.api.infrastructure.web.dto.PaymentStatusName;
import com.regivolley.api.infrastructure.web.dto.RecordPaymentRequest;
import com.regivolley.api.infrastructure.web.dto.SubscriptionPaymentEntryResponse;
import com.regivolley.api.infrastructure.web.dto.SubscriptionResponse;
import com.regivolley.api.infrastructure.web.mapper.SubscriptionCsvWebMapper;
import com.regivolley.api.infrastructure.web.mapper.SubscriptionWebMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Who owes what and the money coming in (US-21, US-22, RN-17, RN-18), for administrators (the use cases check): the list by payment
 * status as JSON or as a CSV download, marking a subscription overdue and recording a payment. Subscriptions are created through the
 * member they are for ({@code POST /members/{memberId}/subscriptions}).
 */
@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionController.class);
    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final ListSubscriptionsByPaymentStatusUseCase listByPaymentStatus;
    private final MarkSubscriptionOverdueUseCase markOverdue;
    private final RecordPaymentUseCase recordPayment;

    public SubscriptionController(ListSubscriptionsByPaymentStatusUseCase listByPaymentStatus, MarkSubscriptionOverdueUseCase markOverdue,
                                  RecordPaymentUseCase recordPayment) {
        this.listByPaymentStatus = listByPaymentStatus;
        this.markOverdue = markOverdue;
        this.recordPayment = recordPayment;
    }

    /**
     * The subscriptions in a payment status whose end date lies in a window of at most two years ({@code endingFrom}, {@code endingTo};
     * one year either side of today when both are absent).
     */
    @GetMapping
    public List<SubscriptionPaymentEntryResponse> list(@CurrentActor AuthenticatedActor caller, @RequestParam PaymentStatusName paymentStatus,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @PlausibleDate LocalDate endingFrom,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @PlausibleDate LocalDate endingTo) {
        return SubscriptionWebMapper.toEntryResponses(
                listByPaymentStatus.execute(SubscriptionWebMapper.listQuery(caller.actor(), paymentStatus, endingFrom, endingTo)));
    }

    /**
     * The same list as a spreadsheet; cells that a spreadsheet would run as a formula are neutralised ({@link SubscriptionCsvWebMapper}).
     * Handing the member list of an association out is audited: one line with ids and the row count, no names (threat model M5).
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@CurrentActor AuthenticatedActor caller, @RequestParam PaymentStatusName paymentStatus,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @PlausibleDate LocalDate endingFrom,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @PlausibleDate LocalDate endingTo) {
        var entries = listByPaymentStatus.execute(SubscriptionWebMapper.listQuery(caller.actor(), paymentStatus, endingFrom, endingTo));
        LOG.info("Payment list exported: associationId={} exportedBy={} status={} rows={}", caller.associationId().value(),
                caller.memberId().value(), paymentStatus, entries.size());
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(SubscriptionCsvWebMapper.fileName(paymentStatus)).build().toString())
                .body(SubscriptionCsvWebMapper.toCsv(entries));
    }

    @PostMapping("/{subscriptionId}/overdue-marking")
    public SubscriptionResponse overdue(@CurrentActor AuthenticatedActor caller, @PathVariable UUID subscriptionId) {
        return SubscriptionWebMapper.toResponse(markOverdue.execute(SubscriptionWebMapper.overdueCommand(caller.actor(), subscriptionId)));
    }

    @PostMapping("/{subscriptionId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentRecordedResponse pay(@CurrentActor AuthenticatedActor caller, @PathVariable UUID subscriptionId,
                                       @Valid @RequestBody RecordPaymentRequest body) {
        return SubscriptionWebMapper.toResponse(recordPayment.execute(SubscriptionWebMapper.recordPaymentCommand(caller.actor(), subscriptionId, body)));
    }
}
