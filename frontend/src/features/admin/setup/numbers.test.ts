import { describe, expect, it } from 'vitest';
import { wholeNumber, wholeNumberHint } from './numbers';

describe('wholeNumber', () => {
  it.each([
    ['1', 1],
    [' 7 ', 7],
    ['007', 7],
    ['1000', 1000],
  ])('reads %j as %i', (text, value) => {
    expect(wholeNumber(text, 1, 1000)).toBe(value);
  });

  it.each(['', ' ', '0', '1001', '1.5', '1,5', '-1', '+1', '1e2', 'abc', '12345678901'])(
    'refuses %j',
    (text) => {
      expect(wholeNumber(text, 1, 1000)).toBeNull();
    },
  );

  it('says the limits', () => {
    expect(wholeNumberHint(1, 7)).toBe('Enter a whole number from 1 to 7.');
  });
});
