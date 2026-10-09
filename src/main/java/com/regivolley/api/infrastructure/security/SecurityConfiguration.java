package com.regivolley.api.infrastructure.security;

import com.regivolley.api.domain.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.session.DisableEncodeUrlFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The security filter chain (threat model section 7): stateless, default-deny with {@link PublicRoutes} as the only exceptions, bearer
 * tokens verified by the pinned ES256 decoder and then resolved to an {@link AuthenticatedActor} by the
 * {@link PrincipalResolver}, JSON 401/403, security headers, a CORS policy that is off unless the {@code dev} profile
 * names a local origin, and the request id and size filters. No sessions, no cookies, no CSRF token (see below), no
 * form login, no basic auth, no actuator.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    private static final List<String> CORS_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private static final List<String> CORS_HEADERS = List.of("Authorization", "Content-Type");
    private static final long HSTS_ONE_YEAR_SECONDS = 31_536_000;

    @Bean
    ApiErrorWriter apiErrorWriter(ObjectMapper mapper) {
        return new ApiErrorWriter(mapper);
    }

    /** The in-memory token buckets (D-9), shared by the per-IP filter and the per-email checks of the services. */
    @Bean
    public RateLimiter rateLimiter(Clock clock) {
        return new RateLimiter(clock);
    }

    /** The one place a token becomes an identity. Without a {@link SecurityAccountLookup} bean the application does not start. */
    @Bean
    public PrincipalResolver principalResolver(SecurityAccountLookup accounts, MemberRepository members) {
        return new PrincipalResolver(accounts, members);
    }

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, JwtDecoder decoder, PrincipalResolver resolver,
                                                      ApiErrorWriter writer, RateLimiter rateLimiter, Environment environment,
                                                      @Value("${regi-volley.security.cors.allowed-origin:}") String allowedOrigin,
                                                      @Value("${regi-volley.security.web-origin:}") String webOrigin)
            throws Exception {
        CorsConfigurationSource cors = corsConfigurationSource(environment, allowedOrigin);
        ApiAuthenticationEntryPoint entryPoint = new ApiAuthenticationEntryPoint(writer);
        ApiAccessDeniedHandler accessDenied = new ApiAccessDeniedHandler(writer);

        http
                // Stateless API: the access token travels in the Authorization header, which a browser never attaches by
                // itself, so there is no ambient credential to forge. The two cookie endpoints get their Origin and
                // content-type checks there. Threat model D-7a. (Semgrep p/java reports nothing for this line, so there
                // is deliberately no suppression comment to rot.)
                .csrf(csrf -> csrf.disable())
                .cors(configurer -> configurer.configurationSource(cors))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(login -> login.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .headers(this::securityHeaders)
                .authorizeHttpRequests(authorize -> {
                    for (PublicRoutes.Route route : PublicRoutes.ALL) {
                        if (route.method() == null) {
                            authorize.requestMatchers(route.pattern()).permitAll();
                        } else {
                            authorize.requestMatchers(route.method(), route.pattern()).permitAll();
                        }
                    }
                    authorize.anyRequest().authenticated();
                })
                .oauth2ResourceServer(resource -> resource
                        .bearerTokenResolver(new HeaderOnlyBearerTokenResolver())
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(new ActorAuthenticationConverter(resolver)))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDenied))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDenied))
                .addFilterBefore(new RequestIdFilter(), DisableEncodeUrlFilter.class)
                .addFilterAfter(new RequestSizeLimitFilter(writer), HeaderWriterFilter.class)
                .addFilterAfter(new RateLimitFilter(rateLimiter, writer), RequestSizeLimitFilter.class)
                .addFilterAfter(new UserRateLimitFilter(rateLimiter, writer), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new CookieEndpointGuardFilter(writer, webOrigins(allowedOrigin, webOrigin)), RateLimitFilter.class)
                .addFilterBefore(new SuppressedEndpointsFilter(writer), CorsFilter.class);
        return http.build();
    }

    /** The origins besides the API's own that may call the cookie endpoints: the configured web origin and, under {@code dev}, the local PWA. */
    private static Set<String> webOrigins(String devOrigin, String webOrigin) {
        Set<String> origins = new HashSet<>();
        Stream.of(devOrigin, webOrigin).map(String::trim).filter(origin -> !origin.isEmpty()).forEach(origins::add);
        return origins;
    }

    private void securityHeaders(HeadersConfigurer<HttpSecurity> headers) {
        headers
                .cacheControl(cache -> cache.disable())
                .contentTypeOptions(Customizer.withDefaults())
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                // Spring emits it on HTTPS requests only, so it takes effect once TLS terminates in front of the app.
                .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(HSTS_ONE_YEAR_SECONDS).includeSubDomains(true))
                .addHeaderWriter(new StaticHeadersWriter("Cache-Control", "no-store"));
    }

    /**
     * CORS is off (same origin, D-7b) unless profile {@code dev} is active and {@code regi-volley.security.cors.allowed-origin}
     * names the local PWA origin: that single origin, explicit methods and headers, credentials only for
     * {@code /api/v1/auth/**}. A wildcard origin is refused at start-up.
     */
    static CorsConfigurationSource corsConfigurationSource(Environment environment, String allowedOrigin) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (allowedOrigin.isBlank() || !environment.acceptsProfiles(Profiles.of("dev"))) {
            return source;
        }
        requireSingleOrigin(allowedOrigin);
        source.registerCorsConfiguration("/api/v1/auth/**", corsFor(allowedOrigin, true));
        source.registerCorsConfiguration("/api/**", corsFor(allowedOrigin, false));
        return source;
    }

    private static CorsConfiguration corsFor(String origin, boolean credentials) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(origin));
        configuration.setAllowedMethods(CORS_METHODS);
        configuration.setAllowedHeaders(CORS_HEADERS);
        configuration.setAllowCredentials(credentials);
        configuration.setMaxAge(600L);
        return configuration;
    }

    private static void requireSingleOrigin(String origin) {
        try {
            URI uri = URI.create(origin);
            boolean valid = uri.getScheme() != null && uri.getHost() != null && (uri.getPath() == null || uri.getPath().isEmpty());
            if (!valid || origin.contains("*") || origin.contains(",")) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("regi-volley.security.cors.allowed-origin must be one explicit origin such as http://localhost:5173");
        }
    }
}
