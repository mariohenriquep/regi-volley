import { describe, expect, it } from 'vitest';
import { cancellationAdvice } from './cancellation';

// Tuesday 14 July 2026, session at 20:30 Lisbon (19:30Z); free until 14:30 Lisbon (13:30Z).
const DEADLINE = '2026-07-14T13:30:00Z';
const at = (instant: string) => new Date(instant).getTime();

describe('cancellationAdvice', () => {
  it('says cancelling is free before the deadline, with the deadline in Lisbon time', () => {
    // Arrange / Act
    const advice = cancellationAdvice('CONFIRMED', DEADLINE, at('2026-07-14T10:00:00Z'));

    // Assert
    expect(advice).toEqual({
      free: true,
      text: 'Cancelling now is free. You can cancel for free until Tue 14 Jul, 14:30.',
    });
  });

  it('counts exactly the deadline as free (inclusive)', () => {
    // Arrange / Act
    const advice = cancellationAdvice('CONFIRMED', DEADLINE, at(DEADLINE));

    // Assert
    expect(advice?.free).toBe(true);
  });

  it('counts a millisecond after the deadline as late', () => {
    // Arrange / Act
    const advice = cancellationAdvice('CONFIRMED', DEADLINE, at(DEADLINE) + 1);

    // Assert
    expect(advice).toEqual({
      free: false,
      text: 'Cancelling now counts as a used session (late). Free cancellation ended Tue 14 Jul, 14:30.',
    });
  });

  it('says a place on the waitlist is always free, whatever the deadline', () => {
    // Arrange / Act
    const advice = cancellationAdvice('WAITLISTED', DEADLINE, at('2026-07-14T19:00:00Z'));

    // Assert
    expect(advice).toEqual({
      free: true,
      text: 'Cancelling a place on the waitlist is always free.',
    });
  });

  it('claims nothing for a confirmed booking whose deadline it cannot read', () => {
    // Arrange / Act / Assert
    expect(cancellationAdvice('CONFIRMED', undefined, Date.now())).toBeUndefined();
  });
});
