package com.regivolley.api.infrastructure.security;

import com.regivolley.api.application.port.CommonPasswordList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The offline list of common passwords (threat model D-8): entries compared in lower case after NFKC normalisation. Bundled as
 * {@code security/common-passwords.txt} (a seed list; a vetted ~10k list replaces it before go-live). Offline on purpose:
 * a breached-password API needs an outbound call and a privacy review, and is deferred.
 */
public final class BundledCommonPasswordList implements CommonPasswordList {

    private static final String RESOURCE = "/security/common-passwords.txt";

    private final Set<String> entries;

    BundledCommonPasswordList(Set<String> entries) {
        Set<String> normalised = new HashSet<>();
        entries.forEach(entry -> normalised.add(canonical(entry)));
        this.entries = Set.copyOf(normalised);
    }

    public static BundledCommonPasswordList load() {
        try (InputStream in = BundledCommonPasswordList.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("The bundled common-password list is missing: " + RESOURCE);
            }
            Set<String> loaded = new HashSet<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String entry = line.strip();
                    if (!entry.isEmpty() && !entry.startsWith("#")) {
                        loaded.add(entry);
                    }
                }
            }
            return new BundledCommonPasswordList(loaded);
        } catch (IOException e) {
            throw new UncheckedIOException("The common-password list could not be read", e);
        }
    }

    @Override
    public boolean contains(String password) {
        return entries.contains(canonical(password));
    }

    int size() {
        return entries.size();
    }

    private static String canonical(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
}
