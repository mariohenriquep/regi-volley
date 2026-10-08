package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.SubmitJoinRequestCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.application.result.JoinRequestSubmitted;
import com.regivolley.api.domain.exception.JoinRequestNotPossibleException;
import com.regivolley.api.domain.exception.ShortNameNotFoundException;
import com.regivolley.api.domain.factory.JoinRequestFactory;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.valueobject.ContactDetails;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
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
 * for two submissions racing, and surfaces as the same exception.
 */
@Service
public class SubmitJoinRequestService implements SubmitJoinRequestUseCase {

    private final AssociationRepository associations;
    private final MemberRepository members;
    private final JoinRequestRepository joinRequests;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public SubmitJoinRequestService(AssociationRepository associations, MemberRepository members,
                                    JoinRequestRepository joinRequests, TransactionRunner transactions, Clock clock) {
        this.associations = associations;
        this.members = members;
        this.joinRequests = joinRequests;
        this.unitOfWork = new UnitOfWork(transactions);
        this.clock = clock;
    }

    @Override
    public JoinRequestSubmitted execute(SubmitJoinRequestCommand command) {
        ShortName shortName = ShortName.of(command.shortName());
        EmailAddress email = EmailAddress.of(command.email());
        ContactDetails contact = ContactDetails.of(command.name(), email, PhoneNumber.of(command.phone()));
        return unitOfWork.retrying(() -> {
            Association association = associations.findByShortName(shortName)
                    .orElseThrow(() -> new ShortNameNotFoundException(shortName));
            if (members.findByEmail(association.id(), email).isPresent()
                    || joinRequests.findPendingByEmail(association.id(), email).isPresent()) {
                throw new JoinRequestNotPossibleException();
            }
            JoinRequest request = JoinRequestFactory.create(association.id(), contact, command.consentAccepted(),
                    command.policyVersion(), clock);
            return new JoinRequestSubmitted(joinRequests.save(request).id());
        });
    }
}
