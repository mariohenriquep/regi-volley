import { describe, expect, it } from 'vitest';
import { attendanceLine, resolveCoachingWeek, toCoachingSessions, toRoster } from './model';
import { coaching, rosterOf, rosterEntry } from '@/test/fixtures';

describe('toCoachingSessions', () => {
  it('reads a line with every count and flag, and keeps the API order', () => {
    // Arrange
    const raw = [
      coaching({ groupName: 'Early' }),
      coaching({ sessionId: 'other', groupName: 'Late' }),
    ];

    // Act
    const sessions = toCoachingSessions(raw);

    // Assert
    expect(sessions.map((session) => session.groupName)).toEqual(['Early', 'Late']);
    expect(sessions[0]).toMatchObject({ capacity: 12, confirmedCount: 8, canCancel: true });
  });

  it('skips a row it cannot read and treats a payload that is not a list as empty', () => {
    // Arrange
    const unreadable = { ...coaching(), startsAt: 'yesterday-ish' };

    // Act / Assert
    expect(toCoachingSessions([unreadable, null, coaching()])).toHaveLength(1);
    expect(toCoachingSessions({ not: 'a list' })).toEqual([]);
  });

  it('defaults a missing count to zero and a missing flag to false, so nothing is offered by accident', () => {
    // Arrange
    const sparse = {
      sessionId: 's-1',
      startsAt: '2026-07-16T19:30:00Z',
      endsAt: '2026-07-16T21:00:00Z',
    };

    // Act
    const [session] = toCoachingSessions([sparse]);

    // Assert
    expect(session).toMatchObject({
      groupName: 'Training session',
      capacity: 0,
      unmarkedCount: 0,
      canMarkAttendance: false,
      canCancel: false,
      canChangeCapacity: false,
    });
  });
});

describe('attendanceLine', () => {
  it('is absent before anything can be marked or has been marked', () => {
    // Arrange
    const [future] = toCoachingSessions([
      coaching({ attendedCount: 0, noShowCount: 0, canMarkAttendance: false }),
    ]);

    // Act / Assert
    expect(future && attendanceLine(future)).toBeNull();
  });

  it('reads attended, no-show and to-mark once the API lets attendance be marked', () => {
    // Arrange
    const [started] = toCoachingSessions([
      coaching({ attendedCount: 5, noShowCount: 1, unmarkedCount: 2, canMarkAttendance: true }),
    ]);

    // Act / Assert
    expect(started && attendanceLine(started)).toBe('5 attended · 1 no-show · 2 to mark');
  });

  it('is absent for a cancelled session', () => {
    // Arrange
    const [cancelled] = toCoachingSessions([coaching({ status: 'CANCELLED', attendedCount: 3 })]);

    // Act / Assert
    expect(cancelled && attendanceLine(cancelled)).toBeNull();
  });
});

describe('resolveCoachingWeek', () => {
  const thisWeek = '2026-07-13';

  it('allows past weeks (attendance is marked after the session) within the limits', () => {
    expect(resolveCoachingWeek('2026-07-06', thisWeek)).toBe('2026-07-06');
    expect(resolveCoachingWeek('2026-09-02', thisWeek)).toBe('2026-08-31');
  });

  it('falls back to this week for anything unreadable or out of range', () => {
    expect(resolveCoachingWeek(null, thisWeek)).toBe(thisWeek);
    expect(resolveCoachingWeek('soon', thisWeek)).toBe(thisWeek);
    expect(resolveCoachingWeek('2020-01-06', thisWeek)).toBe(thisWeek);
    expect(resolveCoachingWeek('2030-01-07', thisWeek)).toBe(thisWeek);
  });
});

describe('toRoster', () => {
  it('keeps seats and waitlist in the order the API gave them', () => {
    // Arrange
    const raw = rosterOf({
      seats: [
        rosterEntry({ memberName: 'Ana' }),
        rosterEntry({ bookingId: 'b-2', memberName: 'Bruno' }),
      ],
      waitlist: [rosterEntry({ bookingId: 'b-3', memberName: 'Carla', status: 'WAITLISTED' })],
    });

    // Act
    const roster = toRoster(raw);

    // Assert
    expect(roster?.seats.map((entry) => entry.name)).toEqual(['Ana', 'Bruno']);
    expect(roster?.waitlist.map((entry) => entry.name)).toEqual(['Carla']);
  });

  it('is null for a payload without a session start', () => {
    expect(toRoster({})).toBeNull();
  });

  it('reads the coaching line the roster carries, with its counts and flags', () => {
    // Arrange
    const raw = rosterOf({
      coaching: coaching({ confirmedCount: 3, canMarkAttendance: true, canChangeCapacity: false }),
    });

    // Act
    const roster = toRoster(raw);

    // Assert
    expect(roster?.line).toMatchObject({
      groupName: 'Thursday evening',
      confirmedCount: 3,
      canMarkAttendance: true,
      canCancel: true,
      canChangeCapacity: false,
    });
  });

  it('has no line when the answer carries none or an unreadable one, so nothing is offered', () => {
    // Arrange
    const withoutLine = rosterOf({ coaching: undefined });
    const unreadable = rosterOf({ coaching: { ...coaching(), startsAt: 'soon' } });

    // Act / Assert
    expect(toRoster(withoutLine)?.line).toBeNull();
    expect(toRoster(unreadable)?.line).toBeNull();
  });
});
