import { describe, expect, it } from 'vitest';
import { centsToInput, formatCents, MAX_PRICE_CENTS, parseEuros } from './money';

describe('formatCents', () => {
  it.each([
    [5000, '€50.00'],
    [0, '€0.00'],
    [1999, '€19.99'],
    [123456, '€1,234.56'],
  ])('writes %i cents as %s', (cents, text) => {
    expect(formatCents(cents)).toBe(text);
  });
});

describe('parseEuros', () => {
  // The cases where floating point goes wrong: 0.1 * 100, 0.2 * 100, 19.99 * 100, 1.15 * 100 are not whole numbers.
  it.each([
    ['0.10', 10],
    ['0.20', 20],
    ['0.30', 30],
    ['19.99', 1999],
    ['1.15', 115],
    ['4.35', 435],
    ['8.2', 820],
    ['0.5', 50],
    ['50', 5000],
    ['50.00', 5000],
    ['0', 0],
    ['0.00', 0],
    ['0.01', 1],
    ['1000000', 100000000],
    ['1000000.00', 100000000],
  ])('reads %s euros as %i cents, exactly', (text, cents) => {
    expect(parseEuros(text)).toBe(cents);
  });

  it('accepts a decimal comma and ignores the spaces around the amount', () => {
    expect(parseEuros(' 19,99 ')).toBe(1999);
    expect(parseEuros('0,10')).toBe(10);
  });

  it.each([
    '',
    '  ',
    'abc',
    '-1',
    '+1',
    '1.234',
    '1,234.56',
    '1.',
    '.5',
    '1e3',
    '€5',
    '5€',
    '1 000',
    '0x10',
  ])('refuses %j', (text) => {
    expect(parseEuros(text)).toBeNull();
  });

  it('refuses more than the API accepts', () => {
    expect(parseEuros('1000000.01')).toBeNull();
    expect(MAX_PRICE_CENTS).toBe(100_000_000);
  });
});

describe('centsToInput', () => {
  it.each([
    [5000, '50.00'],
    [10, '0.10'],
    [1999, '19.99'],
    [0, '0.00'],
    [5, '0.05'],
  ])('writes %i cents as %s', (cents, text) => {
    expect(centsToInput(cents)).toBe(text);
  });

  it('round-trips through parseEuros', () => {
    for (const cents of [0, 1, 10, 20, 99, 100, 1999, 99999, 100_000_000]) {
      expect(parseEuros(centsToInput(cents))).toBe(cents);
    }
  });
});
