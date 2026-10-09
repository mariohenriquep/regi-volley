package com.regivolley.api.infrastructure.security;

import com.regivolley.api.application.port.BackgroundWork;
import com.regivolley.api.application.port.CommonPasswordList;
import com.regivolley.api.application.port.AttemptThrottle;
import com.regivolley.api.application.port.PasswordHasher;
import com.regivolley.api.application.port.SecretGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wiring of the adapters behind the credential use cases' ports: hashing, secrets, the common-password list, the per-email throttle and background work. */
@Configuration
public class CredentialsConfiguration {

    /** Argon2id with bcrypt accepted as legacy, at most four hashes at once (threat model D-8). Built once: it precomputes the dummy hash. */
    @Bean
    public PasswordHasher passwordHasher() {
        return Argon2PasswordHasher.production();
    }

    @Bean
    public SecretGenerator secretGenerator() {
        return new SecureSecretGenerator();
    }

    @Bean
    public CommonPasswordList commonPasswordList() {
        return BundledCommonPasswordList.load();
    }

    @Bean
    public AttemptThrottle credentialAttemptThrottle(RateLimiter limiter) {
        return new RateLimitingAttemptThrottle(limiter);
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorBackgroundWork backgroundWork() {
        return new ExecutorBackgroundWork();
    }
}
