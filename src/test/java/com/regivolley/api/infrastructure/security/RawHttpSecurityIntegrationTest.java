package com.regivolley.api.infrastructure.security;

import com.regivolley.api.application.port.AccessTokenIssuer;

import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.infrastructure.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The real server, the real filter chain and real sockets (threat model sections 7 and 10): what Tomcat does before and after
 * our code. MockMvc cannot show these, because it never parses a wire request nor runs the container's error dispatch.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RawHttpSecurityIntegrationTest.Accounts.class)
class RawHttpSecurityIntegrationTest extends AbstractPostgresIntegrationTest {

    @TestConfiguration
    static class Accounts {
        @Bean
        @org.springframework.context.annotation.Primary
        InMemorySecurityAccountLookup accountLookup() {
            return new InMemorySecurityAccountLookup();
        }
    }

    @LocalServerPort
    private int port;
    @Autowired
    private InMemorySecurityAccountLookup accounts;
    @Autowired
    private AccessTokenIssuer issuer;
    @MockitoBean
    private MemberRepository members;

    private Association association;
    private Member member;
    private UUID userId;

    @BeforeEach
    void seed() {
        accounts.clear();
        association = SecurityFixtures.association();
        member = SecurityFixtures.active(association);
        userId = UUID.randomUUID();
        accounts.registerActive(userId, association.id(), member.id(), "stamp-1");
        when(members.findById(association.id(), member.id())).thenReturn(Optional.of(member));
    }

    private String bearer() {
        return "Bearer " + issuer.issue(userId, association.id(), member.id(), "stamp-1").value();
    }

    /** Sends the bytes as they are and reads the whole answer until the server closes the connection. */
    private String rawExchange(byte[] request) throws Exception {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            try {
                out.write(request);
                out.flush();
            } catch (IOException refusedEarly) {
                // The server may answer and close before it has swallowed everything we sent: the answer is still readable.
            }
            ByteArrayOutputStream answer = new ByteArrayOutputStream();
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[4096];
            try {
                int n;
                while ((n = in.read(buffer)) >= 0) {
                    answer.write(buffer, 0, n);
                }
            } catch (IOException reset) {
                // A reset after the status line is fine: we only need what arrived.
            }
            return answer.toString(StandardCharsets.UTF_8);
        }
    }

    private static String statusLine(String response) {
        return response.lines().findFirst().orElse("");
    }

    @Test
    void aChunkedBodyIsRefusedWith411() throws Exception {
        // Arrange
        String request = "POST /api/v1/me HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n"
                + "5\r\nhello\r\n0\r\n\r\n";

        // Act
        String response = rawExchange(request.getBytes(StandardCharsets.US_ASCII));

        // Assert
        assertThat(statusLine(response)).contains("411");
        assertThat(response).contains("\"code\":\"LENGTH_REQUIRED\"");
    }

    @Test
    void aBodyOverSixtyFourKibIsRefusedWith413() throws Exception {
        // Arrange
        String body = "a".repeat(70_000);
        String request = "POST /api/v1/me HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\nContent-Length: "
                + body.length() + "\r\nConnection: close\r\n\r\n" + body;

        // Act
        String response = rawExchange(request.getBytes(StandardCharsets.US_ASCII));

        // Assert
        assertThat(statusLine(response)).contains("413");
        assertThat(response).contains("\"code\":\"PAYLOAD_TOO_LARGE\"");
    }

    @Test
    void aMalformedRequestLineIsRefusedWithoutDetail() throws Exception {
        // Arrange
        String request = "GET /api v1 me HTTP/1.1 extra\r\nHost: localhost\r\nConnection: close\r\n\r\n";

        // Act
        String response = rawExchange(request.getBytes(StandardCharsets.US_ASCII));

        // Assert
        assertThat(statusLine(response)).contains("400");
        assertThat(response).doesNotContain("Exception").doesNotContain("org.apache").doesNotContain("\tat ");
    }

    @Test
    void traceIsNeverEchoed() throws Exception {
        // Arrange
        String request = "TRACE /api/v1/me HTTP/1.1\r\nHost: localhost\r\nX-Probe: canary-value\r\nConnection: close\r\n\r\n";

        // Act
        String response = rawExchange(request.getBytes(StandardCharsets.US_ASCII));

        // Assert
        assertThat(statusLine(response)).matches(".* (401|403|405) .*");
        assertThat(response).doesNotContain("canary-value");
    }

    @Test
    void anOversizedHeaderBlockIsRefusedWithoutDetail() throws Exception {
        // Arrange
        String request = "GET /api/v1/me HTTP/1.1\r\nHost: localhost\r\nX-Big: " + "h".repeat(10_000) + "\r\nConnection: close\r\n\r\n";

        // Act
        String response = rawExchange(request.getBytes(StandardCharsets.US_ASCII));

        // Assert
        assertThat(statusLine(response)).matches(".* (400|431) ?.*");
        assertThat(response).doesNotContain("Exception").doesNotContain("\tat ");
    }

    @Test
    void aDatabaseFailureWhileResolvingThePrincipalIsAGeneric500NeverAnAuthenticatedRequest() throws Exception {
        // Arrange
        when(members.findById(association.id(), member.id())).thenThrow(new IllegalStateException("db down for ana.silva@example.com"));
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/me"))
                .header("Authorization", bearer()).GET().build();

        // Act
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        // Assert
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).contains("\"code\":\"INTERNAL_ERROR\"").contains("\"message\":\"An unexpected error occurred\"")
                .contains("\"requestId\":\"").doesNotContain("ana.silva").doesNotContain("db down");
        assertThat(response.headers().firstValue("X-Request-Id")).isPresent();
    }

    @Test
    void aValidTokenWorksOverTheRealServer() throws Exception {
        // Arrange
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/me"))
                .header("Authorization", bearer()).GET().build();

        // Act
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        // Assert
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(member.id().toString());
    }
}
