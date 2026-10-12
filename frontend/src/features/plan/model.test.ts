import { describe, expect, it } from 'vitest';
import { creditNote, type Subscription, toPlan } from './model';

const sub = (overrides: Partial<Subscription> = {}): Subscription => ({
  id: 's1',
  planName: 'Pack',
  type: 'PACK',
  startDate: '2026-07-01',
  endDate: '2026-09-29',
  paymentStatus: 'PAID',
  remaining: 7,
  ...overrides,
});

describe('creditNote', () => {
  it('counts the credits of the plans that cover the session day', () => {
    // Arrange
    const subscriptions = [sub(), sub({ id: 's2', remaining: 2 })];

    // Act / Assert
    expect(creditNote(subscriptions, '2026-07-14', false)).toBe("You'll use 1 credit (9 left).");
  });

  it('does not guess for a mix of credit and unlimited plans', () => {
    // Arrange
    const subscriptions = [
      sub(),
      sub({ id: 's2', type: 'MONTHLY_UNLIMITED', remaining: undefined }),
    ];

    // Act / Assert
    expect(creditNote(subscriptions, '2026-07-14', false)).toBe(
      'This may use 1 credit from your plan.',
    );
    expect(creditNote(subscriptions, '2026-07-14', true)).toBe(
      'A credit may be used if you get a seat.',
    );
  });

  it('says nothing when no plan covers the day, or none is made of credits', () => {
    // Arrange / Act / Assert
    expect(creditNote([sub()], '2026-10-06', false)).toBeNull();
    expect(creditNote([sub({ type: 'MONTHLY_N_PER_WEEK' })], '2026-07-14', false)).toBeNull();
    expect(creditNote([], '2026-07-14', false)).toBeNull();
  });
});

describe('toPlan', () => {
  it('drops subscriptions whose dates cannot be read and tolerates a payload without a list', () => {
    // Arrange
    const good = {
      subscriptionId: 'a',
      planName: 'Pack',
      type: 'PACK',
      startDate: '2026-07-01',
      endDate: '2026-09-29',
      paymentStatus: 'PAID',
      remaining: 3,
    };

    // Act
    const plan = toPlan({
      asOf: '2026-07-15',
      subscriptions: [
        good,
        { ...good, subscriptionId: 'b', endDate: '2026-02-30' },
        { ...good, subscriptionId: 'c', startDate: 'x' },
      ],
    });

    // Assert
    expect(plan.subscriptions.map(({ id }) => id)).toEqual(['a']);
    expect(toPlan({ asOf: 'tomorrow', subscriptions: 'x' as never })).toEqual({
      asOf: '',
      subscriptions: [],
    });
  });
});
