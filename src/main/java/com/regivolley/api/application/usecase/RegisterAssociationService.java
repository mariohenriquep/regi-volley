package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.RegisterAssociationCommand;
import com.regivolley.api.application.port.AccountProvisioner;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.application.result.AssociationRegistered;
import com.regivolley.api.domain.exception.ShortNameAlreadyTakenException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.ContactDetails;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.GdprConsent;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.model.valueobject.PhoneNumber;
import com.regivolley.api.domain.model.valueobject.ShortName;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Set;

/**
 * US-01, RN-20. A visitor registers an association: it is created with its levels and the person registering
 * becomes its first member - ACTIVE, an administrator (and a member), at the entry level - in the same
 * transaction, so there is never an association nobody can manage. There is no actor: the visitor has no
 * account yet.
 *
 * <p>The short name is unique across associations: checked first for a clear answer, and backed by the
 * database constraint for two registrations racing for it (both end as {@link ShortNameAlreadyTakenException}).
 * The founder's contact data and the RGPD consent are validated before anything is stored; the consent is
 * stamped by the server clock. Once committed, the founder is given a way to sign in (an activation link by email, see
 * {@link AccountProvisioner}); that step cannot undo the registration.
 */
@Service
public class RegisterAssociationService implements RegisterAssociationUseCase {

    private final AssociationRepository associations;
    private final MemberRepository members;
    private final AccountProvisioner provisioner;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public RegisterAssociationService(AssociationRepository associations, MemberRepository members,
                                      TransactionRunner transactions, AccountProvisioner provisioner, Clock clock) {
        this.associations = associations;
        this.members = members;
        this.provisioner = provisioner;
        this.unitOfWork = new UnitOfWork(transactions);
        this.clock = clock;
    }

    @Override
    public AssociationRegistered execute(RegisterAssociationCommand command) {
        ShortName shortName = ShortName.of(command.shortName());
        Association association = Association.create(command.name(), command.shortName(), command.nif(),
                command.locality(), command.contactEmail(), command.levelNames());
        ContactDetails founderContact = ContactDetails.of(command.founderName(), EmailAddress.of(command.founderEmail()),
                PhoneNumber.of(command.founderPhone()));
        GdprConsent consent = GdprConsent.record(command.consentAccepted(), command.policyVersion(), clock);
        Member founder = Member.create(association, founderContact, consent,
                Set.of(MemberRole.ADMIN, MemberRole.MEMBER), clock);

        Member storedFounder = unitOfWork.retrying(() -> {
            if (associations.existsByShortName(shortName)) {
                throw new ShortNameAlreadyTakenException(shortName);
            }
            associations.save(association);
            return members.save(founder);
        });
        AccountProvisioning.afterCommit(provisioner, storedFounder);
        return new AssociationRegistered(storedFounder.associationId(), storedFounder.id());
    }
}
