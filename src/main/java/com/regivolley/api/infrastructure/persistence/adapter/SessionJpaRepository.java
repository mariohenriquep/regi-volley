package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.infrastructure.persistence.entity.SessionJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data access to sessions. Every query carries the association; bookings are always fetched with their session. */
interface SessionJpaRepository extends JpaRepository<SessionJpaEntity, UUID> {

    @Query("""
            select s from SessionJpaEntity s left join fetch s.bookings
            where s.id = :id and s.associationId = :associationId""")
    Optional<SessionJpaEntity> findByIdAndAssociationId(@Param("id") UUID id,
                                                        @Param("associationId") UUID associationId);

    /**
     * The session row locked for update, without its bookings: a save locks the root first, so concurrent
     * writers queue up on it instead of deadlocking on booking rows. (An outer join cannot be locked in PostgreSQL.)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SessionJpaEntity s where s.id = :id and s.associationId = :associationId")
    Optional<SessionJpaEntity> findForUpdateByIdAndAssociationId(@Param("id") UUID id,
                                                                 @Param("associationId") UUID associationId);

    @Query("""
            select s from SessionJpaEntity s left join fetch s.bookings
            where s.associationId = :associationId and s.startsAt >= :from and s.startsAt < :to
            order by s.startsAt, s.id""")
    List<SessionJpaEntity> findStartingBetween(@Param("associationId") UUID associationId,
                                               @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select s from SessionJpaEntity s left join fetch s.bookings
            where s.associationId = :associationId and s.trainingGroupId = :trainingGroupId
              and s.startsAt >= :from and s.startsAt < :to
            order by s.startsAt, s.id""")
    List<SessionJpaEntity> findByTrainingGroupStartingBetween(@Param("associationId") UUID associationId,
                                                              @Param("trainingGroupId") UUID trainingGroupId,
                                                              @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select s from SessionJpaEntity s left join fetch s.bookings
            where s.associationId = :associationId and s.startsAt < :to and s.endsAt > :from
              and exists (select 1 from BookingJpaEntity b
                          where b.session = s and b.memberId = :memberId and b.status <> 'CANCELLED')
            order by s.startsAt, s.id""")
    List<SessionJpaEntity> findWithLiveBookingOverlapping(@Param("associationId") UUID associationId,
                                                          @Param("memberId") UUID memberId,
                                                          @Param("from") Instant from, @Param("to") Instant to);
}
