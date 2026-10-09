package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ReversePaymentCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.application.result.PaymentReversed;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Payment;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.PaymentRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * RN-19. An administrator reverses a recorded payment: a new payment of the same amount that counts negatively is
 * added, linked to the original and carrying who did it and when. The original is never touched (the port and the
 * table do not allow it). A paid subscription that is owed money again moves back to PENDING. A payment can be
 * reversed once, and a reversal cannot be reversed (record the payment again instead).
 */
@Service
public class ReversePaymentService implements ReversePaymentUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ReversePaymentService.class);

    private final PaymentSettling settling;
    private final PaymentRepository payments;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ReversePaymentService(MemberRepository members, SubscriptionRepository subscriptions,
                                 PaymentRepository payments, TransactionRunner transactions, Clock clock) {
        this.settling = new PaymentSettling(members, subscriptions, payments);
        this.payments = payments;
        this.unitOfWork = new UnitOfWork(transactions);
        this.clock = clock;
    }

    @Override
    public PaymentReversed execute(ReversePaymentCommand command) {
        PaymentReversed reversed = unitOfWork.retrying(() -> {
            var associationId = command.actor().associationId();
            Member admin = settling.requireAdmin(command.actor(), "reverse payments");
            Payment original = Lookups.payment(payments, associationId, command.paymentId());
            var settled = settling.settle(associationId, original.subscriptionId(),
                    ledger -> ledger.reverse(original.id(), admin.id(), clock));
            return new PaymentReversed(settled.payment(), settled.subscription(), settled.ledger().outstanding());
        });
        // Audit line (threat model M5), by id only.
        LOG.info("Payment reversed: associationId={} subscriptionId={} paymentId={} reversalId={} reversedBy={}",
                reversed.reversal().associationId(), reversed.reversal().subscriptionId(), command.paymentId(), reversed.reversal().id(),
                command.actor().memberId());
        return reversed;
    }
}
