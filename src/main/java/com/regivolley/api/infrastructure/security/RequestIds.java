package com.regivolley.api.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;

/**
 * Where the id of the current request lives. {@link RequestIdFilter} puts it in the MDC (for log lines and for code that runs
 * inside the filter chain) and in a request attribute, which survives into the container's {@code ERROR} dispatch where the
 * MDC has already been cleared.
 */
public final class RequestIds {

    /** The MDC key; also what {@code logging.pattern.level} prints. */
    public static final String MDC_KEY = "requestId";
    /** The request attribute that carries the id into an error dispatch. */
    public static final String ATTRIBUTE = RequestIds.class.getName() + ".id";

    private RequestIds() {
    }

    /** The id of the request being handled on this thread, or {@code null} outside a request. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    /** The id of this request: from its attribute (also set on an error dispatch), else from the MDC. */
    public static String of(HttpServletRequest request) {
        Object attribute = request.getAttribute(ATTRIBUTE);
        return attribute instanceof String id ? id : current();
    }
}
