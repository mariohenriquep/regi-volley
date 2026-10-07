package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.MarkAttendanceCommand;
import com.regivolley.api.application.result.AttendanceMarked;

/** US-17: the coach marks who came and who did not; reaching the no-show limit warns (RN-11). */
public interface MarkAttendanceUseCase extends UseCase<MarkAttendanceCommand, AttendanceMarked> {
}
