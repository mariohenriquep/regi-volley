package com.regivolley.api.infrastructure.security;

import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;

/**
 * The real security filter chain, the real advice and the real controllers over a mocked {@link MemberRepository} and the
 * in-memory account lookup: no database. Subclasses share one Spring context unless they add configuration.
 */
@WebMvcTest
@Import({SecurityConfiguration.class, JwtKeyConfiguration.class, SecurityTestConfiguration.class})
@ActiveProfiles("test")
public abstract class AbstractSecuredWebTest {

    protected static final String STAMP = "stamp-1";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected AccessTokenIssuer issuer;
    @Autowired
    protected InMemorySecurityAccountLookup accounts;
    @Autowired
    protected JwtKeySet keys;
    @MockitoBean
    protected MemberRepository members;

    protected Association association;
    protected Member member;
    protected UUID userId;

    @BeforeEach
    void seedAnActiveMember() {
        accounts.clear();
        association = SecurityFixtures.association();
        member = SecurityFixtures.active(association);
        userId = UUID.randomUUID();
        accounts.registerActive(userId, association.id(), member.id(), STAMP);
        when(members.findById(association.id(), member.id())).thenReturn(Optional.of(member));
    }

    /** A valid token for the seeded member. */
    protected String validToken() {
        return issuer.issue(userId, association.id(), member.id(), STAMP).value();
    }

    protected String bearer() {
        return "Bearer " + validToken();
    }
}
