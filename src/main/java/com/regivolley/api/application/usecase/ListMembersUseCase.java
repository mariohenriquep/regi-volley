package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListMembersQuery;
import com.regivolley.api.application.result.MemberListing;

/** US-07, US-08: an administrator lists the association's members. */
public interface ListMembersUseCase extends UseCase<ListMembersQuery, MemberListing> {
}
