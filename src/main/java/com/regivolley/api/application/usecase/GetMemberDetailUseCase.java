package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.GetMemberDetailQuery;
import com.regivolley.api.application.result.MemberDetail;

/** US-04, US-07, US-20: an administrator opens one member. */
public interface GetMemberDetailUseCase extends UseCase<GetMemberDetailQuery, MemberDetail> {
}
