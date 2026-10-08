package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.exception.ShortNameAlreadyTakenException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.ShortName;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.infrastructure.persistence.entity.AssociationJpaEntity;
import com.regivolley.api.infrastructure.persistence.mapper.AssociationPersistenceMapper;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import com.regivolley.api.domain.exception.AssociationModifiedConcurrentlyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * {@link AssociationRepository} on Spring Data JPA. Every save of an existing association moves its
 * version forward by one (level changes included); the unique short name is enforced by the
 * database and reported as {@link ShortNameAlreadyTakenException}.
 */
@Component
public class AssociationRepositoryAdapter implements AssociationRepository {

    private static final String SHORT_NAME_CONSTRAINT = "uq_associations_short_name";

    private final AssociationJpaRepository associations;
    private final EntityManager entityManager;

    public AssociationRepositoryAdapter(AssociationJpaRepository associations, EntityManager entityManager) {
        this.associations = associations;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Association> findById(AssociationId id) {
        return associations.findById(id.value()).map(AssociationPersistenceMapper::toDomain);
    }

    @Override
    @Transactional
    public Optional<Association> findByIdForUpdate(AssociationId id) {
        return associations.findForUpdateById(id.value()).map(AssociationPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Association> findByShortName(ShortName shortName) {
        return associations.findByShortName(shortName.value()).map(AssociationPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByShortName(ShortName shortName) {
        return associations.existsByShortName(shortName.value());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssociationId> findAllIds() {
        return associations.findAllIds().stream().map(AssociationId::new).toList();
    }

    @Override
    @Transactional
    public Association save(Association association) {
        try {
            return WriteSupport.translatingConflicts(() -> AssociationPersistenceMapper.toDomain(
                    WriteSupport.write(associations, entityManager,
                            associations.findForUpdateById(association.id().value()),
                            association.version(), AssociationJpaEntity::new,
                            entity -> AssociationPersistenceMapper.apply(association, entity),
                            AssociationJpaEntity::getVersion, () -> conflict(association))),
                    () -> conflict(association));
        } catch (DataIntegrityViolationException e) {
            if (WriteSupport.violates(e, SHORT_NAME_CONSTRAINT)) {
                ShortNameAlreadyTakenException taken = new ShortNameAlreadyTakenException(association.shortName());
                taken.initCause(e);
                throw taken;
            }
            throw e;
        }
    }

    private static AssociationModifiedConcurrentlyException conflict(Association association) {
        return new AssociationModifiedConcurrentlyException(association.id());
    }
}
