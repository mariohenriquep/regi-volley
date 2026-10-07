package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.DeactivateMemberCommand;
import com.regivolley.api.application.result.MemberDeactivated;

/** US-08: an administrator deactivates a member and their future bookings are cancelled. */
public interface DeactivateMemberUseCase extends UseCase<DeactivateMemberCommand, MemberDeactivated> {
}
