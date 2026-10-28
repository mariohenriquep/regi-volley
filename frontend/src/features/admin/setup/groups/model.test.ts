import { describe, expect, it } from 'vitest';
import type { MemberRow } from '../../members/model';
import { slotText, toCoaches, toGroups } from './model';

const member = (overrides: Partial<MemberRow>): MemberRow => ({
  id: 'm',
  name: 'Name',
  email: undefined,
  levelName: undefined,
  roles: ['MEMBER'],
  status: 'ACTIVE',
  accountActivated: true,
  ...overrides,
});

describe('toGroups', () => {
  it('reads levels, venue, coach and the schedule, dropping what cannot be read', () => {
    // Arrange
    const raw = [
      {
        id: 'g1',
        name: 'Tuesday',
        status: 'ACTIVE',
        acceptedLevels: [{ id: 'l1', name: 'Beginner' }, { id: 'l2' }],
        venue: { id: 'v', name: 'Hall' },
        capacity: 12,
        schedule: [
          { dayOfWeek: 'TUESDAY', startTime: '20:30:00', durationMinutes: 90 },
          { dayOfWeek: 'TUESDAY', startTime: 'late', durationMinutes: 90 },
          { startTime: '21:00', durationMinutes: 60 },
        ],
      },
      { name: 'No id' },
    ];

    // Act
    const groups = toGroups(raw);

    // Assert
    expect(groups).toEqual([
      {
        id: 'g1',
        name: 'Tuesday',
        status: 'ACTIVE',
        acceptedLevels: [{ id: 'l1', name: 'Beginner' }],
        venue: { id: 'v', name: 'Hall' },
        coach: undefined,
        capacity: 12,
        schedule: [{ dayOfWeek: 'TUESDAY', startTime: '20:30', durationMinutes: 90 }],
      },
    ]);
  });

  it('treats a payload that is not a list as empty', () => {
    expect(toGroups({ not: 'a list' })).toEqual([]);
  });
});

describe('slotText', () => {
  it('writes the weekday in words, and an unknown word as the API wrote it', () => {
    expect(slotText({ dayOfWeek: 'TUESDAY', startTime: '20:30', durationMinutes: 90 })).toBe(
      'Tuesday 20:30, 90 min',
    );
    expect(slotText({ dayOfWeek: 'FUNDAY', startTime: '09:00', durationMinutes: 5 })).toBe(
      'FUNDAY 09:00, 5 min',
    );
  });
});

describe('toCoaches', () => {
  it('keeps the active members who hold COACH, and says when the list was cut short', () => {
    // Arrange
    const listing = {
      members: [
        member({ id: 'a', name: 'Coach', roles: ['MEMBER', 'COACH'] }),
        member({ id: 'b', name: 'Admin only', roles: ['MEMBER', 'ADMIN'] }),
        member({ id: 'c', name: 'Former coach', roles: ['MEMBER', 'COACH'], status: 'INACTIVE' }),
      ],
      truncated: true,
    };

    // Act
    const result = toCoaches(listing);

    // Assert
    expect(result).toEqual({ coaches: [{ id: 'a', name: 'Coach' }], incomplete: true });
  });
});
