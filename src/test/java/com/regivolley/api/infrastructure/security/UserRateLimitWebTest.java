package com.regivolley.api.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Threat model U7 / D-9: a valid user hammering the API is limited to 300 calls a minute, per user, whatever the route. */
class UserRateLimitWebTest extends AbstractSecuredWebTest {

    @Test
    void theCallAfterTheLimitIs429WithRetryAfterAndTheUniformBody() throws Exception {
        // Arrange
        String bearer = bearer();
        for (int i = 0; i < RateLimitRule.USER.capacity(); i++) {
            mockMvc.perform(get("/api/v1/me").header("Authorization", bearer)).andExpect(status().isOk());
        }

        // Act
        var limited = mockMvc.perform(get("/api/v1/me").header("Authorization", bearer));

        // Assert
        limited.andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void anotherUserHasABucketOfTheirOwn() throws Exception {
        // Arrange
        String bearer = bearer();
        for (int i = 0; i <= RateLimitRule.USER.capacity(); i++) {
            mockMvc.perform(get("/api/v1/me").header("Authorization", bearer));
        }
        UUID otherUser = UUID.randomUUID();
        accounts.registerActive(otherUser, association.id(), member.id(), STAMP);
        String otherBearer = "Bearer " + issuer.issue(otherUser, association.id(), member.id(), STAMP).value();

        // Act
        var other = mockMvc.perform(get("/api/v1/me").header("Authorization", otherBearer));

        // Assert
        other.andExpect(status().isOk());
    }

    @Test
    void aRequestWithoutAValidTokenIsStill401AndDoesNotSpendAnyUsersBudget() throws Exception {
        // Arrange
        String bearer = bearer();

        // Act
        var anonymous = mockMvc.perform(get("/api/v1/me"));
        var afterwards = mockMvc.perform(get("/api/v1/me").header("Authorization", bearer));

        // Assert
        anonymous.andExpect(status().isUnauthorized());
        afterwards.andExpect(status().isOk());
        assertThat(RateLimitRule.USER.capacity()).isEqualTo(300);
    }
}
