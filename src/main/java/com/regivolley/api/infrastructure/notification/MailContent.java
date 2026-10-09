package com.regivolley.api.infrastructure.notification;

import java.util.Objects;

/** A rendered message: the fixed subject and the same body as plain text and as HTML. */
record MailContent(String subject, String text, String html) {

    MailContent {
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(html, "html must not be null");
    }
}
