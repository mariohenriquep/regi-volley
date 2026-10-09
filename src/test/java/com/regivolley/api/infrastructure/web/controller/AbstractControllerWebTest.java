package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.command.Actor;
import com.regivolley.api.infrastructure.security.AbstractSecuredWebTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Shared plumbing of the controller slice tests: the real security chain, advice and controllers with every use case mocked.
 * Requests carry the seeded member's token unless a test builds its own.
 */
abstract class AbstractControllerWebTest extends AbstractSecuredWebTest {

    private static final AtomicInteger NEXT_CLIENT = new AtomicInteger(1);

    /** A request from an address of its own, so the per-IP limits of the public routes (register 3 an hour, ...) never cross between tests. */
    protected static RequestPostProcessor fromNewClient() {
        int n = NEXT_CLIENT.getAndIncrement();
        String address = "10." + (n / 65000 % 250) + "." + (n / 250 % 250) + "." + (n % 250 + 1);
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    /** The actor the use cases must receive: the token's association and member, nothing from the request. */
    protected Actor expectedActor() {
        return new Actor(association.id(), member.id());
    }

    protected ResultActions authenticated(HttpMethod method, String path) throws Exception {
        return mockMvc.perform(request(method, path).header("Authorization", bearer()));
    }

    protected ResultActions authenticated(HttpMethod method, String path, String json) throws Exception {
        return mockMvc.perform(withJson(request(method, path).header("Authorization", bearer()), json));
    }

    protected ResultActions anonymous(HttpMethod method, String path, String json) throws Exception {
        return mockMvc.perform(withJson(request(method, path).with(fromNewClient()), json));
    }

    protected static MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder builder, String json) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(json);
    }
}
