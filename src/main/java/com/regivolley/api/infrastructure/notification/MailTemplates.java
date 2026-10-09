package com.regivolley.api.infrastructure.notification;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The email templates of issue #40: plain text and simple HTML, one pair per {@link NoticeKind} inside a shared layout, with
 * {@code {{name}}} placeholders. Deliberately not a template engine - a single pass of substitution, so a value that itself looks
 * like a placeholder is never expanded, and every value is treated as data:
 * <ul>
 *   <li>a <em>text</em> value (an association's name, a date, a count) loses control, format and line-separator characters
 *       (header injection, bidi spoofing, invisible padding), is collapsed to one line and cut at {@value #MAX_VALUE_LENGTH}
 *       characters; it is HTML-escaped in the HTML body;</li>
 *   <li>a <em>link</em> value must be an http(s) URL without whitespace; it is printed as is in the text body and HTML-escaped in
 *       the HTML one.</li>
 * </ul>
 * A placeholder without a value is a programming error and fails loudly rather than sending a half-written mail (a
 * {@link PermanentMailFailure}: retrying cannot cure it).
 */
public final class MailTemplates {

    static final int MAX_VALUE_LENGTH = 200;

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");
    private static final Pattern INVISIBLE = Pattern.compile("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}\\s]+");
    private static final Pattern LINK = Pattern.compile("https?://[^\\s<>\"']+");
    /** Text that mail clients turn into something clickable: a scheme separator, {@code www.} and an address. */
    private static final Pattern SCHEME = Pattern.compile("://");
    private static final Pattern WWW = Pattern.compile("(?i)www\\.");
    private static final String ZERO_WIDTH_SPACE = "\u200B";
    private static final String BODY = "@@body@@";

    private final Map<NoticeKind, String> texts = new EnumMap<>(NoticeKind.class);
    private final Map<NoticeKind, String> htmls = new EnumMap<>(NoticeKind.class);

    private MailTemplates() {
    }

    /** Reads every template from the class path; a missing one stops the start-up. */
    public static MailTemplates load() {
        MailTemplates loaded = new MailTemplates();
        String textLayout = resource("layout.txt");
        String htmlLayout = resource("layout.html");
        for (NoticeKind kind : NoticeKind.values()) {
            loaded.texts.put(kind, textLayout.replace(BODY, resource(kind.template() + ".txt").stripTrailing()));
            loaded.htmls.put(kind, htmlLayout.replace(BODY, resource(kind.template() + ".html").stripTrailing()));
        }
        return loaded;
    }

    MailContent render(NoticeKind kind, Map<String, String> textValues, Map<String, String> linkValues) {
        linkValues.forEach((name, value) -> {
            if (!LINK.matcher(value).matches()) {
                throw new PermanentMailFailure("The value of " + name + " is not an http(s) link");
            }
        });
        String text = fill(texts.get(kind), textValues, linkValues, false);
        String html = fill(htmls.get(kind), textValues, linkValues, true);
        return new MailContent(kind.subject(), text, html);
    }

    private static String fill(String template, Map<String, String> textValues, Map<String, String> linkValues, boolean html) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value;
            if (linkValues.containsKey(name)) {
                value = linkValues.get(name);
            } else if (textValues.containsKey(name)) {
                value = clean(textValues.get(name));
            } else {
                throw new PermanentMailFailure("No value for the placeholder " + name);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(html ? escape(value) : value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * One line, no control or invisible characters, at most {@value #MAX_VALUE_LENGTH} characters, and no text a mail client would make
     * clickable: a visitor chooses the association's name, so {@code Win now at https://evil.example} must not become a link in a mail
     * that comes from us. The scheme separator, {@code www.} and {@code @} are broken with a zero-width space - the text stays readable
     * and copyable, only the auto-linking stops. Best effort by design (a bare {@code evil.example} may still be linked by some
     * clients); the join notices also tell a stranger to ignore the message.
     */
    static String clean(String value) {
        String line = INVISIBLE.matcher(value).replaceAll(" ").trim();
        if (line.codePointCount(0, line.length()) > MAX_VALUE_LENGTH) {
            line = line.substring(0, line.offsetByCodePoints(0, MAX_VALUE_LENGTH - 1)) + "\u2026";
        }
        line = SCHEME.matcher(line).replaceAll(":" + ZERO_WIDTH_SPACE + "//");
        line = WWW.matcher(line).replaceAll(match -> match.group().substring(0, 3) + ZERO_WIDTH_SPACE + ".");
        return line.replace("@", ZERO_WIDTH_SPACE + "@");
    }

    static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    private static String resource(String name) {
        try (InputStream in = MailTemplates.class.getResourceAsStream("/mail/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing mail template mail/" + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read mail template mail/" + name, e);
        }
    }
}
