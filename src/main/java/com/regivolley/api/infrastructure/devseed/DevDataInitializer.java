package com.regivolley.api.infrastructure.devseed;

import com.regivolley.api.domain.model.valueobject.ShortName;
import com.regivolley.api.domain.repository.AssociationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Issue #59: on start-up under profile {@code dev} creates the ready-to-use association {@code volei-lisboa}, so the API and the
 * frontend can be tried without registering, reading Mailpit and activating first. Idempotent: when the association exists nothing
 * is touched. A failure is logged (exception class only) and does not stop the application, since the seed is a convenience;
 * resetting the dev database ({@code docker compose down -v}) starts it afresh. Logs ids only, never an address or the password.
 */
@DevSeedOnly
class DevDataInitializer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DevDataInitializer.class);

    private final AssociationRepository associations;
    private final DevDataSeeder seeder;

    DevDataInitializer(AssociationRepository associations, DevDataSeeder seeder) {
        this.associations = associations;
        this.seeder = seeder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (associations.existsByShortName(ShortName.of(DevDataSeeder.SHORT_NAME))) {
            LOG.info("Dev seed already present");
            return;
        }
        try {
            seeder.seed();
        } catch (RuntimeException e) {
            LOG.error("Dev seed failed ({}); reset the dev database to try again: docker compose down -v", e.getClass().getSimpleName());
        }
    }
}
