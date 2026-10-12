import { describe, expect, it } from 'vitest';
import { bookable } from '@/test/fixtures';
import { toSession, toSessions } from './model';

describe('toSession', () => {
  it('reads a session', () => {
    // Arrange / Act
    const session = toSession(bookable({ myBookingStatus: 'CONFIRMED' }));

    // Assert
    expect(session).toMatchObject({
      groupName: 'Tuesday evening',
      freeSeats: 5,
      myStatus: 'CONFIRMED',
    });
  });

  it.each([
    ['startsAt', { startsAt: 'next Tuesday' }],
    ['endsAt', { endsAt: '' }],
  ])('drops a session whose %s cannot be read', (_field, overrides) => {
    // Arrange / Act / Assert
    expect(toSession(bookable(overrides))).toBeNull();
  });

  it('keeps the session but not an unreadable opening time', () => {
    // Arrange / Act
    const session = toSession(bookable({ bookingOpensAt: 'whenever' }));

    // Assert
    expect(session?.bookingOpensAt).toBeUndefined();
  });
});

describe('toSessions', () => {
  it('skips bad rows and tolerates a payload that is not a list', () => {
    // Arrange / Act / Assert
    expect(toSessions([bookable(), { groupName: 'no id' }, null, 'x'])).toHaveLength(1);
    expect(toSessions({ not: 'a list' })).toEqual([]);
    expect(toSessions(undefined)).toEqual([]);
  });
});
