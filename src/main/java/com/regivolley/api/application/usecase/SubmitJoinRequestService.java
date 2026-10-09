package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.SubmitJoinRequestCommand;
import com.regivolley.api.application.port.AttemptThrottle;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.application.result.JoinRequestSubmitted;
import com.regivolley.api.domain.exception.JoinRequestNotPossibleException;
import com.regivolley.api.domain.exception.ShortNameNotFoundException;
import com.regivolley.api.domain.factory.JoinRequestFactory;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.valueobject.ContactDetails;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.GdprConsent;
import com.regivolley.api.domain.model.valueobject.PhoneNumber;
import com.regivolley.api.domain.model.valueobject.ShortName;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.JoinRequestRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * US-05. A visitor asks to join the association with a public short name. The RGPD consent is mandatory and is
 * stamped by the server clock. An email that already belongs to a member of the association, or that already
 * has a request waiting, is refused with one generic {@link JoinRequestNotPossibleException}: the answer must
 * not tell a stranger which addresses are members. The database's unique index on pending emails backs the check
 * for two submissions racing, and surfaces as the same exception. Every attempt is first counted against the
 * (association, email) limit of 3 a day (threat model D-9), whatever its outcome.
 */
@Service
public class SubmitJoinRequestService implements SubmitJoinRequestUseCase {

    private final AssociationRepository associations;
    private final MemberRepository members;
    private final JoinRequestRepository joinRequests;
    private final AttemptThrottle throttle;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public SubmitJoinRequestService(AssociationRepository associations, MemberRepository members,
                                    JoinRequestRepository joinRequests, AttemptThrottle throttle,
                                    TransactionRunner transactions, Clock clock) {
        this.associations = associations;
        this.members = members;
        this.joinRequests = joinRequests;
        this.throttle = throttle;
        this.unitOfWork = new UnitOfWork(transactions);
        this.clock = clock;
    }

    @Override
    public JoinRequestSubmitted execute(SubmitJoinRequestCommand command) {
        // Everything that depends only on the input is checked first, before the throttle and before anything stored is read: if a
        // refusal (consent, contact data, policy version) came after the member / pending lookup, its presence would tell a stranger
        // whether an address is already known (threat model P1, D-14). A text that cannot be a short name names no association.
        ShortName shortName = ShortName.tryOf(command.shortName()).orElseThrow(() -> new ShortNameNotFoundException(command.shortName()));
        EmailAddress email = EmailAddress.of(command.email());
        ContactDetails contact = ContactDetails.of(command.name(), email, PhoneNumber.of(command.phone()));
        GdprConsent consent = GdprConsent.record(command.consentAccepted(), command.policyVersion(), clock);
        throttle.checkJoinRequest(shortName.value(), email.value());
        return unitOfWork.retrying(() -> {
            Association association = associations.findByShortName(shortName)
                    .orElseThrow(() -> new ShortNameNotFoundException(shortName));
            if (members.findByEmail(association.id(), email).isPresent()
                    || joinRequests.findPendingByEmail(association.id(), email).isPresent()) {
                throw new JoinRequestNotPossibleException();
            }
            JoinRequest request = JoinRequestFactory.create(association.id(), contact, consent, clock);
            return new JoinRequestSubmitted(joinRequests.save(request).id());
        });
    }
}
