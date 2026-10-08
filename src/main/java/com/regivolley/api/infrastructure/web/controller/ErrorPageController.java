package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.infrastructure.security.RequestIds;
import com.regivolley.api.infrastructure.web.dto.ApiError;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Boot's error page: errors raised outside a controller (a filter that failed, the container rejecting a request)
 * reach the application through the {@code /error} dispatch and must have the same body as every other error, with no
 * exception text, trace or path (threat model E1). Public by design (see {@code PublicRoutes}): it only ever echoes a
 * generic message for the status the container already chose. Called directly, without an error behind it, it answers 404:
 * there is nothing here. The request id comes from the request attribute, because the MDC is already cleared on an error dispatch.
 */
@RestController
public class ErrorPageController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ApiError> error(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = attribute instanceof Integer code ? code : 404;
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(ApiError.ofStatus(status, RequestIds.of(request)));
    }
}
