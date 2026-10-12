import { describe, expect, it } from 'vitest';
import { isCalendarDay, isInstant } from './dates';

describe('isInstant', () => {
  it.each(['2026-07-14T19:30:00Z', '2026-07-14T19:30:00.123+01:00'])('accepts %s', (value) => {
    // Arrange / Act / Assert
    expect(isInstant(value)).toBe(true);
  });

  it.each(['', 'soon', '2026-13-45T00:00:00Z', undefined, null, 5, {}])('rejects %s', (value) => {
    // Arrange / Act / Assert
    expect(isInstant(value)).toBe(false);
  });
});

describe('isCalendarDay', () => {
  it('accepts a real day', () => {
    // Arrange / Act / Assert
    expect(isCalendarDay('2026-02-28')).toBe(true);
  });

  it.each(['2026-02-30', '2026-7-1', '2026-07-14T10:00:00Z', 'x', undefined, 20260714])(
    'rejects %s',
    (value) => {
      // Arrange / Act / Assert
      expect(isCalendarDay(value)).toBe(false);
    },
  );
});
