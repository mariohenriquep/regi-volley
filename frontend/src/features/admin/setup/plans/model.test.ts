import { describe, expect, it } from 'vitest';
import { type Plan, termsText, toPlans, typeLabel } from './model';

const plan = (overrides: Partial<Plan>): Plan => ({
  id: 'a',
  name: 'Plan',
  type: 'PACK',
  priceCents: 100,
  sessionsPerWeek: undefined,
  credits: undefined,
  validityDays: undefined,
  allowedLevels: [],
  ...overrides,
});

describe('termsText', () => {
  it.each([
    [{ type: 'MONTHLY_UNLIMITED' }, 'Unlimited sessions for one month'],
    [{ type: 'MONTHLY_N_PER_WEEK', sessionsPerWeek: 1 }, '1 session per week for one month'],
    [{ type: 'MONTHLY_N_PER_WEEK', sessionsPerWeek: 3 }, '3 sessions per week for one month'],
    [{ type: 'PACK', credits: 10, validityDays: 90 }, '10 credits, valid for 90 days'],
    [{ type: 'PACK', credits: 1, validityDays: 1 }, '1 credit, valid for 1 day'],
    [{ type: 'SINGLE_SESSION', credits: 1, validityDays: 7 }, '1 session, valid for 7 days'],
  ])('says %j as %s', (overrides, text) => {
    expect(termsText(plan(overrides))).toBe(text);
  });

  it('says nothing for a type this version does not know, or a plan missing its numbers', () => {
    expect(termsText(plan({ type: 'SEASON_TICKET' }))).toBeNull();
    expect(termsText(plan({ type: 'PACK' }))).toBeNull();
  });
});

describe('toPlans', () => {
  it('reads the allowed levels as names with ids, dropping unreadable ones', () => {
    // Arrange
    const raw = [
      {
        id: 'a',
        name: 'Pack',
        type: 'PACK',
        priceCents: 5000,
        allowedLevels: [{ id: 'l1', name: 'Beginner' }, { id: 'l2' }, null],
      },
      { name: 'No id', priceCents: 1 },
      { id: 'b', name: 'No price' },
    ];

    // Act
    const plans = toPlans(raw);

    // Assert
    expect(plans).toHaveLength(1);
    expect(plans[0]?.allowedLevels).toEqual([{ id: 'l1', name: 'Beginner' }]);
  });

  it('treats a payload that is not a list as empty', () => {
    expect(toPlans({ not: 'a list' })).toEqual([]);
  });
});

describe('typeLabel', () => {
  it('uses the words of the form, and shows an unknown type as written', () => {
    expect(typeLabel('PACK')).toBe('Pack of sessions');
    expect(typeLabel('SEASON_TICKET')).toBe('SEASON_TICKET');
  });
});
