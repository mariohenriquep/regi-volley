import { describe, expect, it } from 'vitest';
import { ApiError, type RefusalReason } from '@/api/errors';
import { adviceFor } from './bookingAdvice';

const refusal = (reason: string | undefined, status = 422) =>
  new ApiError(
    status,
    'BUSINESS_RULE_VIOLATION',
    'Some message',
    [],
    'req-1',
    undefined,
    reason as RefusalReason | undefined,
  );

describe('adviceFor', () => {
  it.each([
    ['PAYMENT_OVERDUE', 'Contact your association to settle the payment', true],
    ['NO_BALANCE', 'You have no sessions left on your plan.', true],
    ['NO_VALID_SUBSCRIPTION', 'You need a plan that is valid on the day of the session.', true],
    ['PLAN_LEVEL_NOT_ALLOWED', "Your plan doesn't cover this group's levels.", true],
    ['LEVEL_NOT_ALLOWED', 'This group is for other levels.', false],
    ['MEMBER_INACTIVE', 'Contact your association about your membership.', false],
    ['BOOKING_NOT_OPEN', 'Come back when booking opens.', false],
    ['BOOKING_CLOSED', 'Booking for this session is over.', false],
    ['OVERLAP', 'Cancel your other booking first', false],
    ['SESSION_NOT_SCHEDULED', "This session isn't available any more.", false],
    ['COACH_OF_SESSION', 'You coach this session', false],
    ['CANCELLATION_CLOSED', "It's too late to cancel this booking.", false],
  ])('%s: tells the member what to do', (reason, text, planLink) => {
    // Arrange / Act
    const advice = adviceFor(refusal(reason));

    // Assert
    expect(advice?.text).toContain(text);
    expect(advice?.planLink ?? false).toBe(planLink);
  });

  it('DUPLICATE_BOOKING (a 409): says the member already has a booking', () => {
    // Arrange / Act
    const advice = adviceFor(refusal('DUPLICATE_BOOKING', 409));

    // Assert
    expect(advice?.text).toBe('You already have a booking in this session.');
  });

  it('gives no advice for a reason it does not know, so only the API message shows', () => {
    // Arrange / Act / Assert
    expect(adviceFor(refusal('SOMETHING_NEW'))).toBeUndefined();
    expect(adviceFor(refusal('constructor'))).toBeUndefined();
  });

  it('gives no advice without a reason, whatever the message says', () => {
    // Arrange
    const error = new ApiError(422, 'BUSINESS_RULE_VIOLATION', "The member's payment is overdue");

    // Act / Assert
    expect(adviceFor(error)).toBeUndefined();
  });

  it('gives no advice for what is not an API error', () => {
    // Arrange / Act / Assert
    expect(adviceFor(new Error('boom'))).toBeUndefined();
    expect(adviceFor(null)).toBeUndefined();
  });
});
