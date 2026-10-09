package com.regivolley.api.infrastructure.persistence.adapter;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A JPA slice against the real PostgreSQL of {@code AbstractPostgresIntegrationTest} (Flyway
 * migrations applied, {@code ddl-auto: validate}) with every repository adapter wired in. Each test
 * runs in a transaction that is rolled back.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AssociationRepositoryAdapter.class, EmailLinkStoreAdapter.class, JoinRequestRepositoryAdapter.class,
        MemberRepositoryAdapter.class, MembershipStoreAdapter.class, RefreshTokenStoreAdapter.class,
        SecurityAccountLookupAdapter.class, UserAccountStoreAdapter.class, PaymentRepositoryAdapter.class, PlanRepositoryAdapter.class, SessionRepositoryAdapter.class, SubscriptionRepositoryAdapter.class,
        TrainingGroupRepositoryAdapter.class, VenueRepositoryAdapter.class})
public @interface PersistenceTest {
}
