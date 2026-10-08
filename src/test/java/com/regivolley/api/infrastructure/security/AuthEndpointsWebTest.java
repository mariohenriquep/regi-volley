package com.regivolley.api.infrastructure.security;

import com.regivolley.api.application.command.ActivateAccountCommand;
import com.regivolley.api.application.command.LoginCommand;
import com.regivolley.api.application.command.LogoutAllCommand;
import com.regivolley.api.application.command.LogoutCommand;
import com.regivolley.api.application.command.RefreshSessionCommand;
import com.regivolley.api.application.command.RequestPasswordResetCommand;
import com.regivolley.api.application.command.ResetPasswordCommand;
import com.regivolley.api.application.exception.InvalidCredentialsException;
import com.regivolley.api.application.exception.InvalidLinkException;
import com.regivolley.api.application.exception.InvalidRefreshTokenException;
import com.regivolley.api.application.exception.RateLimitExceededException;
import com.regivolley.api.application.exception.ServiceBusyException;
import com.regivolley.api.application.result.SessionTokens;
import com.regivolley.api.domain.exception.InvalidFieldException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The HTTP contract of the credential endpoints over the real filter chain, advice and controller; the use cases are mocks. */
class AuthEndpointsWebTest extends AbstractSecuredWebTest {

    private static final AtomicInteger NEXT_CLIENT = new AtomicInteger(1);
    private static final String COOKIE = "__Secure-rt";
    private static final String LOGIN_BODY = json("email", "ana@example.com", "password", "a long passphrase");

    private String clientIp;

    @BeforeEach
    void uniqueClient() {
        // One rate-limit bucket set is shared by the whole Spring context: every test is a different "client".
        int n = NEXT_CLIENT.getAndIncrement();
        clientIp = "198.51." + (n / 250) + "." + (n % 250 + 1);
    }

    private RequestPostProcessor fromClient() {
        return request -> {
            request.setRemoteAddr(clientIp);
            return request;
        };
    }

    private SessionTokens tokens(String refresh) {
        return new SessionTokens(issuer.issue(userId, association.id(), member.id(), STAMP).value(), 600, refresh,
                SecurityFixtures.NOW.plus(Duration.ofDays(30)));
    }

    private static String json(String... pairs) {
        StringBuilder out = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            out.append(i == 0 ? "" : ",").append('"').append(pairs[i]).append("\":\"").append(pairs[i + 1]).append('"');
        }
        return out.append('}').toString();
    }

    private MockHttpServletRequestBuilder jsonPost(String path, String body) {
        return post(path).with(fromClient()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder cookiePost(String path, String cookieValue) {
        return jsonPost(path, "{}").cookie(new Cookie(COOKIE, cookieValue));
    }

    // ---- login -------------------------------------------------------------------------------------------------

    @Test
    void loginReturnsTheAccessTokenInTheBodyAndTheRefreshTokenOnlyInAHardenedCookie() throws Exception {
        // Arrange
        when(loginUseCase.execute(any(LoginCommand.class))).thenReturn(tokens("refresh-value-1"));

        // Act
        ResultActions result = mockMvc.perform(jsonPost("/api/v1/auth/login", LOGIN_BODY));

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(600))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("refresh-value-1"))))
                .andExpect(header().string("Cache-Control", "no-store"));
        String cookie = result.andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie).startsWith(COOKIE + "=refresh-value-1;").contains("Path=/api/v1/auth").contains("HttpOnly").contains("Secure")
                .contains("SameSite=Strict").contains("Max-Age=" + Duration.ofDays(30).toSeconds()).doesNotContain("Domain=");
    }

    @Test
    void loginPassesTheEmailThePasswordAndOnlyTheConnectionsAddress() throws Exception {
        // Arrange
        when(loginUseCase.execute(any(LoginCommand.class))).thenReturn(tokens("r"));

        // Act
        mockMvc.perform(jsonPost("/api/v1/auth/login", LOGIN_BODY).header("X-Forwarded-For", "10.9.8.7"));

        // Assert
        verify(loginUseCase).execute(new LoginCommand("ana@example.com", "a long passphrase", clientIp));
    }

    @Test
    void aFailedLoginIsTheUniform401WithNoCookieAndNoBearerChallenge() throws Exception {
        // Arrange
        when(loginUseCase.execute(any(LoginCommand.class))).thenThrow(new InvalidCredentialsException());

        // Act
        ResultActions result = mockMvc.perform(jsonPost("/api/v1/auth/login", LOGIN_BODY));

        // Assert
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void aStaleBearerHeaderDoesNotTurnTheLoginIntoA401() throws Exception {
        // Arrange
        when(loginUseCase.execute(any(LoginCommand.class))).thenReturn(tokens("r"));

        // Act
        ResultActions result = mockMvc.perform(jsonPost("/api/v1/auth/login", LOGIN_BODY).header("Authorization", "Bearer garbage"));

        // Assert
        result.andExpect(status().isOk());
    }

    @Test
    void loginRejectsMissingFieldsNamingThemButNeverTheirValues() throws Exception {
        // Arrange
        String empty = "{}";

        // Act
        ResultActions result = mockMvc.perform(jsonPost("/api/v1/auth/login", empty));

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0]").value("email"))
                .andExpect(jsonPath("$.fields[1]").value("password"));
        verifyNoInteractions(loginUseCase);
    }

    @Test
    void loginRejectsUnknownProperties() throws Exception {
        // Arrange
        String withRole = "{\"email\":\"a@b.co\",\"password\":\"p\",\"role\":\"ADMIN\"}";

        // Act
        ResultActions result = mockMvc.perform(jsonPost("/api/v1/auth/login", withRole));

        // Assert
        result.andExpect(status().isBadRequest());
        verifyNoInteractions(loginUseCase);
    }

    @Test
    void loginRequiresJson() throws Exception {
        // Arrange
        MockHttpServletRequestBuilder form = post("/api/v1/auth/login").with(fromClient()).contentType(MediaType.TEXT_PLAIN)
                .content("email=a&password=b");

        // Act
        ResultActions result = mockMvc.perform(form);

        // Assert
        result.andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void aThrottledLoginIs429WithRetryAfter() throws Exception {
        // Arrange
        when(loginUseCase.execute(any(LoginCommand.class))).thenThrow(new RateLimitExceededException(Duration.ofSeconds(90)));

        // Act
        ResultActions result = mockMvc.perform(jsonPost("/api/v1/auth/login", LOGIN_BODY));

        // Assert
        result.andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "90"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void whenEveryHashingSlotIsBusyTheAnswerIs503WithRetryAfter() throws Exception {
        // Arrange
        when(loginUseCase.execute(any(LoginCommand.class))).thenThrow(new ServiceBusyException(2));

        // Act
        ResultActions result = mockMvc.perform(jsonPost("/api/v1/auth/login", LOGIN_BODY));

        // Assert
        result.andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "2"))
                .andExpect(jsonPath("$.code").value("SERVICE_BUSY"));
    }

    // ---- refresh -----------------------------------------------------------------------------------------------

    @Test
    void refreshRotatesTheCookieAndReturnsANewAccessToken() throws Exception {
        // Arrange
        when(refreshSessionUseCase.execute(new RefreshSessionCommand("old-refresh"))).thenReturn(tokens("new-refresh"));

        // Act
        ResultActions result = mockMvc.perform(cookiePost("/api/v1/auth/refresh", "old-refresh"));

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("new-refresh"))));
        assertThat(result.andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE)).startsWith(COOKIE + "=new-refresh;")
                .contains("HttpOnly").contains("Secure").contains("SameSite=Strict").contains("Path=/api/v1/auth");
    }

    @Test
    void refreshWithoutACookieIsTheConstant401() throws Exception {
        // Arrange
        when(refreshSessionUseCase.execute(new RefreshSessionCommand(null))).thenThrow(new InvalidRefreshTokenException());

        // Act
        ResultActions result = mockMvc.perform(jsonPost("/api/v1/auth/refresh", "{}"));

        // Assert
        result.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void refreshWithABadCookieIs401AndSetsNoCookie() throws Exception {
        // Arrange
        when(refreshSessionUseCase.execute(new RefreshSessionCommand("bad"))).thenThrow(new InvalidRefreshTokenException());

        // Act
        ResultActions result = mockMvc.perform(cookiePost("/api/v1/auth/refresh", "bad"));

        // Assert
        result.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void theRefreshTokenIsReadFromTheCookieNeverFromTheBodyOrTheQuery() throws Exception {
        // Arrange
        when(refreshSessionUseCase.execute(new RefreshSessionCommand(null))).thenThrow(new InvalidRefreshTokenException());

        // Act
        ResultActions result = mockMvc.perform(jsonPost("/api/v1/auth/refresh", "{\"refreshToken\":\"x\"}").queryParam("refreshToken", "x"));

        // Assert
        result.andExpect(status().isUnauthorized());
        verify(refreshSessionUseCase, never()).execute(new RefreshSessionCommand("x"));
    }

    // ---- D-7a: Origin and content type on the two cookie endpoints ----------------------------------------------

    @Test
    void refreshRequiresAJsonContentType() throws Exception {
        // Arrange - a plain HTML form cannot send application/json, so a forged cross-site form is refused outright
        MockHttpServletRequestBuilder form = post("/api/v1/auth/refresh").with(fromClient()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .cookie(new Cookie(COOKIE, "r"));

        // Act
        ResultActions result = mockMvc.perform(form);

        // Assert
        result.andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        verifyNoInteractions(refreshSessionUseCase);
    }

    @Test
    void refreshWithoutAnyContentTypeIsRefusedToo() throws Exception {
        // Arrange
        MockHttpServletRequestBuilder bare = post("/api/v1/auth/refresh").with(fromClient()).cookie(new Cookie(COOKIE, "r"));

        // Act
        ResultActions result = mockMvc.perform(bare);

        // Assert
        result.andExpect(status().isUnsupportedMediaType());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/refresh", "/api/v1/auth/logout"})
    void theCookieRoutesRefuseAForeignOrigin(String path) throws Exception {
        // Arrange
        MockHttpServletRequestBuilder forged = cookiePost(path, "r").header("Origin", "https://evil.example");

        // Act
        ResultActions result = mockMvc.perform(forged);

        // Assert
        result.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(refreshSessionUseCase, logoutUseCase);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/%72efresh", "/api/v1/auth/%6cogout"})
    void anEncodedSpellingOfTheCookieRoutesDoesNotSlipPastTheOriginCheck(String path) throws Exception {
        // Arrange - Spring MVC decodes %72 to r and routes this to the refresh handler; the guard must see it the same way
        MockHttpServletRequestBuilder forged = post(URI.create(path)).with(fromClient()).contentType(MediaType.APPLICATION_JSON)
                .header("Origin", "https://evil.example").cookie(new Cookie(COOKIE, "r")).content("{}");

        // Act
        ResultActions result = mockMvc.perform(forged);

        // Assert
        result.andExpect(status().isForbidden());
        verifyNoInteractions(refreshSessionUseCase, logoutUseCase);
    }

    @Test
    void theNullOriginIsRefusedToo() throws Exception {
        // Arrange
        MockHttpServletRequestBuilder sandboxed = cookiePost("/api/v1/auth/refresh", "r").header("Origin", "null");

        // Act
        ResultActions result = mockMvc.perform(sandboxed);

        // Assert
        result.andExpect(status().isForbidden());
    }

    @Test
    void aBrowserThatSaysTheRequestIsCrossSiteIsRefusedWhateverItsOrigin() throws Exception {
        // Arrange
        MockHttpServletRequestBuilder crossSite = cookiePost("/api/v1/auth/refresh", "r").header("Sec-Fetch-Site", "cross-site")
                .header("Origin", "http://localhost");

        // Act
        ResultActions result = mockMvc.perform(crossSite);

        // Assert
        result.andExpect(status().isForbidden());
        verifyNoInteractions(refreshSessionUseCase);
    }

    @ParameterizedTest
    @ValueSource(strings = {"same-origin", "same-site", "none"})
    void otherFetchSiteValuesAreNotRefused(String fetchSite) throws Exception {
        // Arrange
        when(refreshSessionUseCase.execute(any(RefreshSessionCommand.class))).thenReturn(tokens("n"));
        MockHttpServletRequestBuilder sameSite = cookiePost("/api/v1/auth/refresh", "r").header("Sec-Fetch-Site", fetchSite);

        // Act
        ResultActions result = mockMvc.perform(sameSite);

        // Assert
        result.andExpect(status().isOk());
    }

    @Test
    void theApisOwnOriginIsAccepted() throws Exception {
        // Arrange - MockMvc's default request is http://localhost
        when(refreshSessionUseCase.execute(any(RefreshSessionCommand.class))).thenReturn(tokens("n"));

        // Act
        ResultActions result = mockMvc.perform(cookiePost("/api/v1/auth/refresh", "r").header("Origin", "http://localhost"));

        // Assert
        result.andExpect(status().isOk());
    }

    @Test
    void aMissingOriginIsAccepted() throws Exception {
        // Arrange
        when(refreshSessionUseCase.execute(any(RefreshSessionCommand.class))).thenReturn(tokens("n"));

        // Act
        ResultActions result = mockMvc.perform(cookiePost("/api/v1/auth/refresh", "r"));

        // Assert
        result.andExpect(status().isOk());
    }

    @Test
    void theOriginCheckDoesNotApplyToTheRoutesThatCarryNoCookie() throws Exception {
        // Arrange - they use the Authorization header or a body secret, so there is no ambient credential to forge
        when(loginUseCase.execute(any(LoginCommand.class))).thenReturn(tokens("r"));

        // Act
        ResultActions result = mockMvc.perform(jsonPost("/api/v1/auth/login", LOGIN_BODY).header("Origin", "https://other.example"));

        // Assert
        result.andExpect(status().isOk());
    }

    // ---- logout ------------------------------------------------------------------------------------------------

    @Test
    void logoutEndsTheLoginAndClearsTheCookie() throws Exception {
        // Arrange
        MockHttpServletRequestBuilder request = cookiePost("/api/v1/auth/logout", "r");

        // Act
        ResultActions result = mockMvc.perform(request);

        // Assert
        result.andExpect(status().isNoContent());
        verify(logoutUseCase).execute(new LogoutCommand("r"));
        assertThat(result.andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE)).startsWith(COOKIE + "=;").contains("Max-Age=0")
                .contains("Path=/api/v1/auth").contains("HttpOnly").contains("Secure").contains("SameSite=Strict");
    }

    @Test
    void logoutWithoutACookieIsStill204() throws Exception {
        // Arrange
        MockHttpServletRequestBuilder request = jsonPost("/api/v1/auth/logout", "{}");

        // Act
        ResultActions result = mockMvc.perform(request);

        // Assert
        result.andExpect(status().isNoContent());
        verify(logoutUseCase).execute(new LogoutCommand(null));
    }

    @Test
    void logoutAllNeedsAValidAccessToken() throws Exception {
        // Arrange
        MockHttpServletRequestBuilder anonymous = post("/api/v1/auth/logout-all").with(fromClient());

        // Act
        ResultActions result = mockMvc.perform(anonymous);

        // Assert
        result.andExpect(status().isUnauthorized());
        verifyNoInteractions(logoutAllUseCase);
    }

    @Test
    void logoutAllEndsEverySessionOfTheCallerAndClearsTheCookie() throws Exception {
        // Arrange
        MockHttpServletRequestBuilder request = post("/api/v1/auth/logout-all").with(fromClient()).header("Authorization", bearer());

        // Act
        ResultActions result = mockMvc.perform(request);

        // Assert
        result.andExpect(status().isNoContent());
        verify(logoutAllUseCase).execute(new LogoutAllCommand(userId));
        assertThat(result.andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }

    // ---- activation and reset ----------------------------------------------------------------------------------

    @Test
    void activatingWithAValidLinkIs204() throws Exception {
        // Arrange
        String body = json("token", "link-token", "password", "a long passphrase");

        // Act
        ResultActions result = mockMvc.perform(jsonPost("/api/v1/auth/activate", body));

        // Assert
        result.andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(activateAccountUseCase).execute(new ActivateAccountCommand("link-token", "a long passphrase"));
    }

    @Test
    void resettingWithAValidLinkIs204() throws Exception {
        // Arrange
        String body = json("token", "link-token", "password", "a long passphrase");

        // Act
        ResultActions result = mockMvc.perform(jsonPost("/api/v1/auth/password-resets", body));

        // Assert
        result.andExpect(status().isNoContent());
        verify(resetPasswordUseCase).execute(new ResetPasswordCommand("link-token", "a long passphrase"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/activate", "/api/v1/auth/password-resets"})
    void anInvalidLinkIsA400WithAConstantMessage(String path) throws Exception {
        // Arrange
        when(activateAccountUseCase.execute(any())).thenThrow(new InvalidLinkException());
        when(resetPasswordUseCase.execute(any())).thenThrow(new InvalidLinkException());

        // Act
        ResultActions result = mockMvc.perform(jsonPost(path, json("token", "link-token", "password", "a long passphrase")));

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LINK"))
                .andExpect(jsonPath("$.message").value("The link is invalid or has expired"));
    }

    @Test
    void aWeakPasswordIsA422NamingThePasswordFieldAndNeverItsValue() throws Exception {
        // Arrange
        when(resetPasswordUseCase.execute(any())).thenThrow(new InvalidFieldException("password", "This password is too common, choose another one"));

        // Act
        ResultActions result = mockMvc.perform(jsonPost("/api/v1/auth/password-resets", json("token", "link-token", "password", "password123")));

        // Assert
        result.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_FIELD"))
                .andExpect(jsonPath("$.fields[0]").value("password"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password123"))));
    }

    @Test
    void activationRequiresThePassword() throws Exception {
        // Arrange
        String withoutPassword = "{\"token\":\"x\"}";

        // Act
        ResultActions result = mockMvc.perform(jsonPost("/api/v1/auth/activate", withoutPassword));

        // Assert
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields[0]").value("password"));
    }

    @Test
    void aResetRequiresTheToken() throws Exception {
        // Arrange
        String withoutToken = "{\"password\":\"a long passphrase\"}";

        // Act
        ResultActions result = mockMvc.perform(jsonPost("/api/v1/auth/password-resets", withoutToken));

        // Assert
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields[0]").value("token"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"known@example.com", "unknown@example.com", "not-an-email"})
    void aResetRequestIsAlways202ReceivedWhateverTheEmail(String email) throws Exception {
        // Arrange
        String body = json("email", email);

        // Act
        ResultActions result = mockMvc.perform(jsonPost("/api/v1/auth/password-reset-requests", body));

        // Assert
        result.andExpect(status().isAccepted()).andExpect(content().json("{\"status\":\"RECEIVED\"}"));
        verify(requestPasswordResetUseCase).execute(new RequestPasswordResetCommand(email));
    }

    @Test
    void aThrottledResetRequestIs429() throws Exception {
        // Arrange
        doThrow(new RateLimitExceededException(Duration.ofMinutes(20))).when(requestPasswordResetUseCase).execute(any());

        // Act
        ResultActions result = mockMvc.perform(jsonPost("/api/v1/auth/password-reset-requests", json("email", "known@example.com")));

        // Assert
        result.andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "1200"));
    }

    // ---- per-IP rate limits (filter, before any use case runs) ---------------------------------------------------

    @Test
    void theThirtyFirstLoginFromOneAddressIsThrottledBeforeAnyUseCaseRuns() throws Exception {
        // Arrange
        when(loginUseCase.execute(any(LoginCommand.class))).thenThrow(new InvalidCredentialsException());
        for (int i = 0; i < RateLimitRule.LOGIN_IP.capacity(); i++) {
            mockMvc.perform(jsonPost("/api/v1/auth/login", LOGIN_BODY)).andExpect(status().isUnauthorized());
        }

        // Act
        ResultActions result = mockMvc.perform(jsonPost("/api/v1/auth/login", LOGIN_BODY));

        // Assert
        result.andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        verify(loginUseCase, times(RateLimitRule.LOGIN_IP.capacity())).execute(any(LoginCommand.class));
    }

    @Test
    void anotherAddressIsNotAffected() throws Exception {
        // Arrange
        when(loginUseCase.execute(any(LoginCommand.class))).thenReturn(tokens("r"));
        for (int i = 0; i <= RateLimitRule.LOGIN_IP.capacity(); i++) {
            mockMvc.perform(jsonPost("/api/v1/auth/login", LOGIN_BODY));
        }
        RequestPostProcessor other = request -> {
            request.setRemoteAddr("192.0.2.200");
            return request;
        };

        // Act
        ResultActions result = mockMvc.perform(post("/api/v1/auth/login").with(other).contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY));

        // Assert
        result.andExpect(status().isOk());
    }

    @Test
    void aSpoofedForwardedForHeaderDoesNotGiveAFreshBucket() throws Exception {
        // Arrange - the limiter keys on the connection's address; X-Forwarded-For from an untrusted peer is ignored
        when(loginUseCase.execute(any(LoginCommand.class))).thenThrow(new InvalidCredentialsException());
        for (int i = 0; i < RateLimitRule.LOGIN_IP.capacity(); i++) {
            mockMvc.perform(jsonPost("/api/v1/auth/login", LOGIN_BODY).header("X-Forwarded-For", "10.0.0." + i));
        }

        // Act
        ResultActions result = mockMvc.perform(jsonPost("/api/v1/auth/login", LOGIN_BODY).header("X-Forwarded-For", "203.0.113.250"));

        // Assert
        result.andExpect(status().isTooManyRequests());
    }

    @Test
    void ipv6AddressesOfOneSlashSixtyFourShareTheLoginBucket() throws Exception {
        // Arrange
        when(loginUseCase.execute(any(LoginCommand.class))).thenThrow(new InvalidCredentialsException());
        for (int i = 0; i < RateLimitRule.LOGIN_IP.capacity(); i++) {
            int host = i + 1;
            mockMvc.perform(post("/api/v1/auth/login").with(request -> {
                request.setRemoteAddr("2001:db8:" + Integer.toHexString(clientIp.hashCode() & 0xffff) + ":1::" + host);
                return request;
            }).contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY));
        }

        // Act
        ResultActions result = mockMvc.perform(post("/api/v1/auth/login").with(request -> {
            request.setRemoteAddr("2001:db8:" + Integer.toHexString(clientIp.hashCode() & 0xffff) + ":1:aaaa:bbbb:cccc:dddd");
            return request;
        }).contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY));

        // Assert
        result.andExpect(status().isTooManyRequests());
    }

    @Test
    void registeringAnAssociationIsLimitedToThreeAnHourPerAddress() throws Exception {
        // Arrange - the controller arrives in 26c; the filter already guards the route
        for (int i = 0; i < RateLimitRule.REGISTER_IP.capacity(); i++) {
            mockMvc.perform(jsonPost("/api/v1/public/associations", "{}")).andExpect(status().isNotFound());
        }

        // Act
        ResultActions result = mockMvc.perform(jsonPost("/api/v1/public/associations", "{}"));

        // Assert
        result.andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test
    void anEncodedSpellingOfARateLimitedRouteSharesItsBucket() throws Exception {
        // Arrange
        for (int i = 0; i < RateLimitRule.REGISTER_IP.capacity(); i++) {
            mockMvc.perform(post(URI.create("/api/v1/public/%61ssociations")).with(fromClient()).contentType(MediaType.APPLICATION_JSON).content("{}"));
        }

        // Act
        ResultActions result = mockMvc.perform(jsonPost("/api/v1/public/associations", "{}"));

        // Assert
        result.andExpect(status().isTooManyRequests());
    }

    @Test
    void joinRequestsAreLimitedToFiveAnHourPerAddressAcrossAssociations() throws Exception {
        // Arrange
        for (int i = 0; i < RateLimitRule.JOIN_IP.capacity(); i++) {
            mockMvc.perform(jsonPost("/api/v1/public/associations/club-" + i + "/join-requests", "{}")).andExpect(status().isNotFound());
        }

        // Act
        ResultActions result = mockMvc.perform(jsonPost("/api/v1/public/associations/another-club/join-requests", "{}"));

        // Assert
        result.andExpect(status().isTooManyRequests());
    }

    @Test
    void passwordResetRequestsAreLimitedPerAddressToo() throws Exception {
        // Arrange
        for (int i = 0; i < RateLimitRule.RESET_IP.capacity(); i++) {
            mockMvc.perform(jsonPost("/api/v1/auth/password-reset-requests", json("email", "u" + i + "@example.com"))).andExpect(status().isAccepted());
        }

        // Act
        ResultActions result = mockMvc.perform(jsonPost("/api/v1/auth/password-reset-requests", json("email", "last@example.com")));

        // Assert
        result.andExpect(status().isTooManyRequests());
    }

    @Test
    void activationAndResetConfirmationShareAGenerousPerAddressLimit() throws Exception {
        // Arrange
        String body = json("token", "link-token", "password", "a long passphrase");
        for (int i = 0; i < RateLimitRule.LINK_TOKEN_IP.capacity(); i++) {
            String path = i % 2 == 0 ? "/api/v1/auth/activate" : "/api/v1/auth/password-resets";
            mockMvc.perform(jsonPost(path, body)).andExpect(status().isNoContent());
        }

        // Act
        ResultActions result = mockMvc.perform(jsonPost("/api/v1/auth/activate", body));

        // Assert
        result.andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test
    void refreshHasItsOwnGenerousLimit() throws Exception {
        // Arrange
        when(refreshSessionUseCase.execute(any(RefreshSessionCommand.class))).thenReturn(tokens("n"));
        for (int i = 0; i < RateLimitRule.REFRESH_IP.capacity(); i++) {
            mockMvc.perform(cookiePost("/api/v1/auth/refresh", "r")).andExpect(status().isOk());
        }

        // Act
        ResultActions result = mockMvc.perform(cookiePost("/api/v1/auth/refresh", "r"));

        // Assert
        result.andExpect(status().isTooManyRequests());
    }

    @Test
    void thePublicPageIsLimitedPerAddress() throws Exception {
        // Arrange
        for (int i = 0; i < RateLimitRule.PUBLIC_PAGE_IP.capacity(); i++) {
            mockMvc.perform(get("/api/v1/public/associations/some-club").with(fromClient())).andExpect(status().isNotFound());
        }

        // Act
        ResultActions result = mockMvc.perform(get("/api/v1/public/associations/some-club").with(fromClient()));

        // Assert
        result.andExpect(status().isTooManyRequests());
    }
}
