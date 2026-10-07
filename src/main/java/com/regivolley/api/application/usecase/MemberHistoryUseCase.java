package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.MemberHistoryQuery;
import com.regivolley.api.application.result.MemberHistory;

/** US-18: the member's last three months of bookings and their no-show standing. */
public interface MemberHistoryUseCase extends UseCase<MemberHistoryQuery, MemberHistory> {
}
