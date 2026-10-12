package com.regivolley.api.infrastructure.devseed;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a bean that exists only for the development data seed (issue #59): profile {@code dev} and never {@code prod} (so
 * {@code prod,dev} has none of it), and only while {@code regi-volley.dev-seed.enabled} is {@code true}, which
 * {@code application-dev.yml} sets and no other profile does. Every bean of this package carries it, so none of the seed can be
 * reached in a real deployment or in a test that did not ask for it.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
@Profile("dev & !prod")
@ConditionalOnProperty(name = "regi-volley.dev-seed.enabled", havingValue = "true")
@interface DevSeedOnly {
}
