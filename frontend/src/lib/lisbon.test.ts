import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  addDays,
  dayHeading,
  durationMinutes,
  formatDateTime,
  formatTime,
  lisbonDay,
  longDate,
  mondayOf,
  weekRange,
} from './lisbon';

afterEach(() => {
  vi.unstubAllEnvs();
});

describe('Lisbon time', () => {
  it.each(['Pacific/Auckland', 'America/Los_Angeles', 'UTC'])(
    'shows the same wall-clock time whatever the device time zone (%s)',
    (timeZone) => {
      // Arrange: 19:30 UTC is 20:30 in Lisbon in summer time (UTC+1)
      vi.stubEnv('TZ', timeZone);

      // Act / Assert
      expect(formatTime('2026-07-14T19:30:00Z')).toBe('20:30');
      expect(lisbonDay('2026-07-14T19:30:00Z')).toBe('2026-07-14');
    },
  );

  it('follows the clock change: the same UTC hour is an hour earlier on the wall in winter', () => {
    // Arrange / Act / Assert
    expect(formatTime('2026-01-13T19:30:00Z')).toBe('19:30');
    expect(formatTime('2026-07-14T19:30:00Z')).toBe('20:30');
  });

  it('puts a late-evening UTC instant on the next Lisbon day', () => {
    // Arrange: 23:30 UTC in summer is 00:30 the next day in Lisbon
    vi.stubEnv('TZ', 'America/Los_Angeles');

    // Act / Assert
    expect(lisbonDay('2026-07-14T23:30:00Z')).toBe('2026-07-15');
    expect(formatTime('2026-07-14T23:30:00Z')).toBe('00:30');
  });

  it('names the day and the date', () => {
    // Arrange / Act / Assert
    expect(dayHeading('2026-07-14')).toBe('Tuesday 14 July');
  });

  it('formats a date and time with the weekday, for "booking opens at"', () => {
    // Arrange / Act / Assert
    expect(formatDateTime('2026-07-14T19:30:00Z')).toBe('Tue 14 Jul, 20:30');
  });

  it('measures a duration in minutes', () => {
    // Arrange / Act / Assert
    expect(durationMinutes('2026-07-14T19:30:00Z', '2026-07-14T21:00:00Z')).toBe(90);
  });
});

describe('calendar days', () => {
  it.each([
    ['2026-07-13', '2026-07-13'],
    ['2026-07-14', '2026-07-13'],
    ['2026-07-19', '2026-07-13'],
    ['2026-07-20', '2026-07-20'],
    ['2026-01-01', '2025-12-29'],
  ])('the Monday of %s is %s', (day, monday) => {
    // Arrange / Act / Assert
    expect(mondayOf(day)).toBe(monday);
  });

  it('adds days across a month and a year, and back', () => {
    // Arrange / Act / Assert
    expect(addDays('2026-12-28', 7)).toBe('2027-01-04');
    expect(addDays('2026-03-02', -7)).toBe('2026-02-23');
  });

  it('has no clock-change drift when adding weeks over the spring change', () => {
    // Arrange / Act / Assert: Lisbon moves to summer time on 2026-03-29
    expect(addDays('2026-03-23', 7)).toBe('2026-03-30');
  });
});

describe('week range', () => {
  it('names a week inside a month and a week across two months', () => {
    // Arrange / Act / Assert
    expect(weekRange('2026-07-13')).toBe('13 – 19 July');
    expect(weekRange('2026-06-29')).toBe('29 June – 5 July');
  });
});

describe('long date', () => {
  it('writes a calendar day in full', () => {
    // Arrange / Act / Assert
    expect(longDate('2026-07-01')).toBe('1 July 2026');
  });
});
