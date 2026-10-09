package com.regivolley.api.application.port;

import com.regivolley.api.application.identity.AccountLink;
import com.regivolley.api.application.identity.AccountLinkPurpose;
import com.regivolley.api.domain.model.valueobject.EmailAddress;

/**
 * Outbound port that delivers a single-use link to the mailbox of the <em>account</em> (threat model D-11): the address the
 * person proved they own by signing up, not a member's contact address, which an administrator can edit or which may belong to
 * someone else. The {@link AccountLink#token() token} is the secret of the URL: the adapter may only put it in the message,
 * never in a log. Called after the commit; a failure never undoes it.
 */
public interface AccountLinkMailer {

    void send(EmailAddress accountEmail, AccountLinkPurpose purpose, AccountLink link);
}
