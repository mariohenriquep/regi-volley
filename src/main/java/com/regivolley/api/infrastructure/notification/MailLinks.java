package com.regivolley.api.infrastructure.notification;

import com.regivolley.api.application.identity.AccountLinkPurpose;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * The links in the emails: the frontend's origin ({@code WEB_ORIGIN}) plus the route that handles the link ({@code /activate},
 * {@code /reset-password}, see {@link NoticeKind#path()}), with the token in the <em>fragment</em> ({@code #token=...}) as the threat
 * model prescribes (D-11): a fragment is not sent to the server, so it is in no access log and no {@code Referer}; the frontend reads it,
 * scrubs it from the address bar, then {@code POST}s the token with the new password.
 *
 * <p>The origin must already be normalised ({@code scheme://host[:port]}, no trailing slash): {@code MailSettings} is the one place
 * that normalises it.
 */
public final class MailLinks {

    private final String origin;

    public MailLinks(String origin) {
        Objects.requireNonNull(origin, "origin must not be null");
        if (origin.endsWith("/")) {
            throw new IllegalArgumentException("The origin must not end with a slash");
        }
        this.origin = origin;
    }

    /** The link for a purpose; the token is percent-encoded, so nothing in it can end the fragment or add markup. */
    String forAccountLink(AccountLinkPurpose purpose, String token) {
        return origin + NoticeKind.of(purpose).path() + "#token=" + UriUtils.encode(token, StandardCharsets.UTF_8);
    }
}
