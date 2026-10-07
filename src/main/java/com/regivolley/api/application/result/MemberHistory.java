package com.regivolley.api.application.result;

import java.util.List;

/**
 * The member's last three months (US-18), newest first, with this month's no-show count against the
 * association's limit. {@code nearLimit} is true from one no-show before the limit on (RN-11: only a
 * warning, nothing is blocked).
 */
public record MemberHistory(List<HistoryEntry> entries, int noShowsThisMonth, int noShowLimit, boolean nearLimit) {

    public MemberHistory {
        entries = List.copyOf(entries);
    }
}
