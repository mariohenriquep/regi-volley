package com.regivolley.api.application.usecase;

import com.regivolley.api.application.port.CommonPasswordList;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.model.valueobject.EmailAddress;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;

/**
 * What a new password must satisfy (threat model D-8): 10 to 128 characters after Unicode NFKC normalisation, no composition
 * rules and no forced rotation (they push people to predictable choices), not on the offline common-password list, and not
 * the email address, its local part or the name of the association. A breached-password API is deferred. A refusal is an
 * {@link InvalidFieldException} naming the field {@code password}, never quoting the value.
 */
final class PasswordPolicy {

    static final int MIN_LENGTH = 10;
    static final int MAX_LENGTH = 128;
    static final String FIELD = "password";

    private final CommonPasswordList common;

    PasswordPolicy(CommonPasswordList common) {
        this.common = common;
    }

    /**
     * @param password       as typed
     * @param email          the account's address
     * @param forbiddenNames other strings the password must not equal, such as the association's name
     * @throws InvalidFieldException if the password does not satisfy the policy
     */
    void check(String password, EmailAddress email, Collection<String> forbiddenNames) {
        if (password == null || password.isBlank()) {
            throw new InvalidFieldException(FIELD, "The password is required");
        }
        String normalised = normalise(password);
        int length = normalised.codePointCount(0, normalised.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new InvalidFieldException(FIELD, "The password must have between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters");
        }
        if (common.contains(normalised)) {
            throw new InvalidFieldException(FIELD, "This password is too common, choose another one");
        }
        String lower = normalised.toLowerCase(Locale.ROOT);
        String address = email.value();
        String localPart = address.substring(0, address.indexOf('@'));
        if (lower.equals(address) || lower.equals(localPart)
                || forbiddenNames.stream().anyMatch(name -> lower.equals(normalise(name).toLowerCase(Locale.ROOT)))) {
            throw new InvalidFieldException(FIELD, "The password must not be your email address or the name of your association");
        }
    }

    /** NFKC, so visually identical passwords typed on different devices are the same password. */
    String normalise(String password) {
        return Normalizer.normalize(password, Normalizer.Form.NFKC);
    }
}
