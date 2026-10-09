package com.regivolley.api.infrastructure.config;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The outbound email settings as the environment gives them (issue #40): {@code SMTP_HOST}, {@code SMTP_PORT},
 * {@code SMTP_USERNAME}, {@code SMTP_PASSWORD}, {@code SMTP_SECURITY} ({@code STARTTLS}, {@code TLS} or {@code NONE}),
 * {@code MAIL_FROM}, and {@code WEB_ORIGIN} for the links. Mail is <em>configured</em> when a host is set; outside profiles
 * {@code dev} and {@code test} it must then be complete and encrypted ({@link #productionProblems()}), otherwise the application
 * does not start. The password is a secret: {@link #toString()} shows neither it nor the user name.
 */
public record MailSettings(String host, int port, String username, String password, String from, Security security,
                           String webOrigin) {

    /** The frontend a link points to when {@code WEB_ORIGIN} is unset: only ever used under {@code dev} / {@code test}. */
    static final String LOCAL_FRONTEND = "http://localhost:5173";
    static final String LOCAL_SENDER = "RegiVolley <no-reply@localhost>";
    static final int DEFAULT_PORT = 587;

    /** How the connection to the mail server is protected. */
    public enum Security {
        /** Plain connection upgraded with STARTTLS, which is required (port 587). */
        STARTTLS,
        /** TLS from the first byte (SMTPS, port 465). */
        TLS,
        /** No encryption: a local catcher such as Mailpit under {@code dev} only. */
        NONE
    }

    public static MailSettings read(Environment environment) {
        return new MailSettings(text(environment.getProperty("regi-volley.mail.host")),
                parsePort(text(environment.getProperty("regi-volley.mail.port"))),
                text(environment.getProperty("regi-volley.mail.username")),
                text(environment.getProperty("regi-volley.mail.password")),
                text(environment.getProperty("regi-volley.mail.from")),
                parseSecurity(text(environment.getProperty("regi-volley.mail.security"))),
                normaliseOrigin(environment.getProperty("regi-volley.security.web-origin")));
    }

    public boolean configured() {
        return !host.isEmpty();
    }

    /** The origin links are built on: {@code WEB_ORIGIN}, or the local frontend when it is unset (outside dev / test start-up refuses that). */
    public String linkOrigin() {
        return webOrigin.isEmpty() ? LOCAL_FRONTEND : webOrigin;
    }

    /** The address mail comes from: {@code MAIL_FROM}, or a local placeholder when it is unset (outside dev / test start-up refuses that). */
    public String senderAddress() {
        return from.isEmpty() ? LOCAL_SENDER : from;
    }

    /**
     * What is wrong with the settings under <em>every</em> profile once a host is set, one line per problem naming the environment
     * variable: a port outside 1-65535, a security mode that names nothing, no encryption towards a host that is not on this machine
     * (the account links would cross the network in clear), and a {@code MAIL_FROM} that is set but is not an address. What a
     * production start-up additionally needs is in {@link #productionProblems()}.
     */
    public List<String> structuralProblems() {
        List<String> problems = new ArrayList<>();
        if (port < 1 || port > 65_535) {
            problems.add("SMTP_PORT must be a port number (1-65535)");
        }
        if (security == null) {
            problems.add("SMTP_SECURITY must be STARTTLS, TLS or NONE");
        } else if (security == Security.NONE && !isLocalHost(host)) {
            problems.add("SMTP_SECURITY=NONE is allowed only for localhost or mailpit: unencrypted mail to another host would expose the account links");
        }
        if (!from.isEmpty() && !isAddress(from)) {
            problems.add("MAIL_FROM must be an email address, optionally with a display name");
        }
        return problems;
    }

    /** What is wrong for a production start-up: everything in {@link #structuralProblems()} plus what must be set and encrypted. */
    public List<String> productionProblems() {
        List<String> problems = new ArrayList<>();
        if (host.isEmpty()) {
            problems.add("SMTP_HOST must be set");
        }
        problems.addAll(structuralProblems());
        if (security == Security.NONE && isLocalHost(host)) {
            problems.add("SMTP_SECURITY must be STARTTLS or TLS outside profiles dev and test");
        }
        if (username.isEmpty()) {
            problems.add("SMTP_USERNAME must be set");
        }
        if (password.isEmpty()) {
            problems.add("SMTP_PASSWORD must be set");
        }
        if (from.isEmpty()) {
            problems.add("MAIL_FROM must be set");
        }
        if (!isHttpsOrigin(webOrigin)) {
            problems.add("WEB_ORIGIN must be the https origin of the frontend (scheme://host[:port], no path): the links in the emails are built on it");
        }
        return problems;
    }

    /** A hint, not an error: the usual port of the other mode (587 speaks STARTTLS, 465 implicit TLS), logged at start-up. */
    public Optional<String> portWarning() {
        if (security == Security.TLS && port == DEFAULT_PORT) {
            return Optional.of("SMTP_SECURITY=TLS with SMTP_PORT=587: port 587 usually speaks STARTTLS, implicit TLS is usually on 465");
        }
        if (security == Security.STARTTLS && port == 465) {
            return Optional.of("SMTP_SECURITY=STARTTLS with SMTP_PORT=465: port 465 usually speaks implicit TLS (SMTP_SECURITY=TLS)");
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return "MailSettings[host=" + host + ", port=" + port + ", security=" + security + ", credentials=redacted]";
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static int parsePort(String raw) {
        if (raw.isEmpty()) {
            return DEFAULT_PORT;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** {@code null} for a value that names no mode, so start-up can refuse it instead of guessing; blank means the default. */
    private static Security parseSecurity(String raw) {
        if (raw.isEmpty()) {
            return Security.STARTTLS;
        }
        try {
            return Security.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String normaliseOrigin(String raw) {
        String origin = text(raw);
        return origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
    }

    private static final Pattern LOOPBACK_V4 = Pattern.compile("127(\\.\\d{1,3}){3}");

    /** This machine or the development mail catcher's service name; a literal check, no name is ever resolved. */
    private static boolean isLocalHost(String host) {
        String name = host.toLowerCase(Locale.ROOT);
        return name.equals("localhost") || name.equals("mailpit") || name.equals("::1") || name.equals("[::1]")
                || LOOPBACK_V4.matcher(name).matches();
    }

    private static boolean isAddress(String value) {
        if (value.isEmpty()) {
            return false;
        }
        try {
            InternetAddress[] parsed = InternetAddress.parse(value, true);
            return parsed.length == 1 && parsed[0].getAddress().contains("@");
        } catch (AddressException e) {
            return false;
        }
    }

    private static boolean isHttpsOrigin(String origin) {
        if (origin.isEmpty()) {
            return false;
        }
        try {
            URI uri = new URI(origin);
            return "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null
                    && (uri.getRawPath() == null || uri.getRawPath().isEmpty())
                    && uri.getRawQuery() == null && uri.getRawFragment() == null;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
