package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.RecordPaymentCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.application.result.PaymentRecorded;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.PaymentRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * US-21, RN-17, RN-18. An administrator records money received for a subscription: amount, date, method, with who
 * recorded it and when. <b>Partial payments are allowed, overpayments are not</b>: the subscription becomes PAID
 * when what has been paid (payments minus reversals) reaches the price it was sold at, and stays as it was until then. The
 * rules are the ledger's; this loads, asks and stores (see {@link PaymentSettling}).
 */
@Service
public class RecordPaymentService implements RecordPaymentUseCase {

    private final PaymentSettling settling;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public RecordPaymentService(MemberRepository members, SubscriptionRepository subscriptions,
                                PaymentRepository payments, TransactionRunner transactions, Clock clock) {
        this.settling = new PaymentSettling(members, subscriptions, payments);
        this.unitOfWork = new UnitOfWork(transactions);
        this.clock = clock;
    }

    @Override
    public PaymentRecorded execute(RecordPaymentCommand command) {
        return unitOfWork.retrying(() -> {
            Member admin = settling.requireAdmin(command.actor(), "record payments");
            var settled = settling.settle(command.actor().associationId(), command.subscriptionId(),
                    ledger -> ledger.record(command.amount(), command.paidOn(), command.method(), admin.id(), clock));
            return new PaymentRecorded(settled.payment(), settled.subscription(), settled.ledger().outstanding());
        });
    }
}
