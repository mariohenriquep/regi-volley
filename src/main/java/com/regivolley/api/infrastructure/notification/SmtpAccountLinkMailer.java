package com.regivolley.api.infrastructure.notification;

import com.regivolley.api.application.identity.AccountLink;
import com.regivolley.api.application.identity.AccountLinkPurpose;
import com.regivolley.api.application.port.AccountLinkMailer;
import com.regivolley.api.domain.model.valueobject.EmailAddress;

import java.util.Map;

/**
 * {@link AccountLinkMailer} over SMTP (issue #40, threat model G3): mails the activation or password-reset link to the
 * <em>account's</em> address (#31 S1) - the address is whatever the caller passes, which is {@code UserAccount.email()}, never a member's
 * editable contact address. The send is queued on the {@link MailDispatcher}, so the call returns at once and a failing mail server
 * is retried in the background. The token lives in the queued task and in the mail only: the task is described to the logs by the
 * link's reference, never by the token, the address or the body.
 */
public final class SmtpAccountLinkMailer implements AccountLinkMailer {

    private final MailDispatcher dispatcher;
    private final MailDelivery delivery;
    private final MailTemplates templates;
    private final MailLinks links;

    public SmtpAccountLinkMailer(MailDispatcher dispatcher, MailDelivery delivery, MailTemplates templates, MailLinks links) {
        this.dispatcher = dispatcher;
        this.delivery = delivery;
        this.templates = templates;
        this.links = links;
    }

    @Override
    public void send(EmailAddress accountEmail, AccountLinkPurpose purpose, AccountLink link) {
        NoticeKind kind = NoticeKind.of(purpose);
        String description = purpose + " link " + link.reference();
        dispatcher.submit(description, () -> {
            MailContent content = templates.render(kind, Map.of("expires", MailFormats.lisbon(link.expiresAt())),
                    Map.of("link", links.forAccountLink(purpose, link.token())));
            delivery.deliver(accountEmail.value(), content);
        });
    }
}
