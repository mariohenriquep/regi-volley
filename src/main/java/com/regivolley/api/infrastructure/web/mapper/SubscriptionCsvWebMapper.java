package com.regivolley.api.infrastructure.web.mapper;

import com.regivolley.api.application.result.SubscriptionPaymentEntry;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.infrastructure.web.dto.PaymentStatusName;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The payment-status list as CSV for a spreadsheet (US-22). Every cell is text a person typed or the system produced, so it is
 * written defensively: quoted per RFC 4180 when it holds a comma, quote or line break, and - because a spreadsheet runs a cell whose
 * first real character is {@code =}, {@code +}, {@code -}, {@code @}, a tab or a carriage return as a formula, and skips spaces,
 * no-break and zero-width spaces, a byte order mark, newlines, vertical tabs and form feeds on the way - given a leading apostrophe
 * in that case (OWASP "CSV injection"). The file starts with a UTF-8 byte order mark so Excel reads accented names correctly, and
 * rows end with CRLF.
 */
public final class SubscriptionCsvWebMapper {

    static final String BYTE_ORDER_MARK = "\uFEFF";
    static final String HEADER = "subscription_id,member_id,member_name,plan_type,start_date,end_date,payment_status,price_eur";

    private SubscriptionCsvWebMapper() {
    }

    public static byte[] toCsv(List<SubscriptionPaymentEntry> entries) {
        String rows = entries.stream().map(SubscriptionCsvWebMapper::row).collect(Collectors.joining());
        return (BYTE_ORDER_MARK + HEADER + "\r\n" + rows).getBytes(StandardCharsets.UTF_8);
    }

    /** The download's name: the status in lower case, so a file for the overdue list does not look like the paid one. */
    public static String fileName(PaymentStatusName status) {
        return "subscriptions-" + status.name().toLowerCase(Locale.ROOT) + ".csv";
    }

    private static String row(SubscriptionPaymentEntry entry) {
        Subscription subscription = entry.subscription();
        return String.join(",",
                cell(subscription.id().value().toString()),
                cell(subscription.memberId().value().toString()),
                cell(entry.memberName()),
                cell(subscription.type().name()),
                cell(subscription.startDate().toString()),
                cell(subscription.endDate().toString()),
                cell(subscription.paymentStatus().name()),
                cell(subscription.price().euros().toPlainString())) + "\r\n";
    }

    /** Characters a spreadsheet skips (or that show as nothing) before it decides whether a cell is a formula. */
    private static final String IGNORED_BEFORE_A_FORMULA = " \u00A0\u200B\uFEFF\n\u000B\u000C";
    private static final String FORMULA_STARTERS = "=+-@\t\r";

    /** One safe cell. Package-private for the unit test. */
    static String cell(String value) {
        String text = value == null ? "" : value;
        if (startsAFormula(text)) {
            text = "'" + text;
        }
        boolean needsQuotes = text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0;
        return needsQuotes ? '"' + text.replace("\"", "\"\"") + '"' : text;
    }

    /** Whether the first character that is not blank or invisible is one a spreadsheet reads as the start of a formula. */
    private static boolean startsAFormula(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (FORMULA_STARTERS.indexOf(c) >= 0) {
                return true;
            }
            if (IGNORED_BEFORE_A_FORMULA.indexOf(c) < 0) {
                return false;
            }
        }
        return false;
    }
}
