package com.regivolley.api.infrastructure.notification;

import com.regivolley.api.application.identity.AccountLink;
import com.regivolley.api.application.identity.AccountLinkPurpose;
import com.regivolley.api.application.port.AccountLinkMailer;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stand-in {@link AccountLinkMailer} used when no SMTP host is configured (profiles dev and test only; the SMTP adapter is {@code SmtpAccountLinkMailer}): records that a link
 * is due and which one, by its reference. It never logs the link's token - that is a credential, it would let anyone set the
 * password - nor the address. The SMTP adapter replaces this class and reads the address and token from the call only to build the
 * message.
 */
public class LoggingAccountLinkMailer implements AccountLinkMailer {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingAccountLinkMailer.class);

    @Override
    public void send(EmailAddress accountEmail, AccountLinkPurpose purpose, AccountLink link) {
        LOG.info("Mail due: {} link issued to the account's address (link={}, expiresAt={})", purpose, link.reference(), link.expiresAt());
    }
}
