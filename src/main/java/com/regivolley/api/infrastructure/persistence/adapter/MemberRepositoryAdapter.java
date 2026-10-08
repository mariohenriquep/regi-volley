package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.exception.MemberEmailAlreadyUsedException;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.infrastructure.persistence.entity.MemberJpaEntity;
import com.regivolley.api.infrastructure.persistence.mapper.MemberPersistenceMapper;
import org.springframework.dao.DataIntegrityViolationException;
import com.regivolley.api.domain.exception.MemberModifiedConcurrentlyException;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** {@link MemberRepository} on Spring Data JPA. Nothing here logs a name, email or phone. */
@Component
public class MemberRepositoryAdapter implements MemberRepository {

    private static final String EMAIL_CONSTRAINT = "uq_members_association_email";

    private final MemberJpaRepository members;
    private final EntityManager entityManager;

    public MemberRepositoryAdapter(MemberJpaRepository members, EntityManager entityManager) {
        this.members = members;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Member> findById(AssociationId associationId, MemberId id) {
        return members.findByIdAndAssociationId(id.value(), associationId.value())
                .map(MemberPersistenceMapper::toDomain);
    }

    @Override
    @Transactional
    public Optional<Member> findByIdForUpdate(AssociationId associationId, MemberId id) {
        return members.findForUpdateByIdAndAssociationId(id.value(), associationId.value())
                .map(MemberPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Member> findByEmail(AssociationId associationId, EmailAddress email) {
        return members.findByAssociationIdAndEmail(associationId.value(), email.value())
                .map(MemberPersistenceMapper::toDomain);
    }

    @Override
    @Transactional
    public Member save(Member member) {
        try {
            return WriteSupport.translatingConflicts(() -> MemberPersistenceMapper.toDomain(
                    WriteSupport.write(members, entityManager,
                            members.findForUpdateByIdAndAssociationId(member.id().value(), member.associationId().value()),
                            member.version(), MemberJpaEntity::new,
                            entity -> MemberPersistenceMapper.apply(member, entity),
                            MemberJpaEntity::getVersion, () -> conflict(member))),
                    () -> conflict(member));
        } catch (DataIntegrityViolationException e) {
            if (WriteSupport.violates(e, EMAIL_CONSTRAINT)) {
                // The cause is dropped on purpose: it could quote the email address.
                throw new MemberEmailAlreadyUsedException();
            }
            throw e;
        }
    }

    private static MemberModifiedConcurrentlyException conflict(Member member) {
        return new MemberModifiedConcurrentlyException(member.id());
    }
}
