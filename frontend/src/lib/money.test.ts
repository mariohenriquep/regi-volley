import { describe, expect, it } from 'vitest';
import { formatCents } from './money';

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
