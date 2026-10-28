/**
 * The API states money in cents and names no currency. The product serves Portuguese associations (Lisbon time, PT-PT
 * requirements), so amounts are shown in euros; a second currency would need the API to say which.
 */
const euro = new Intl.NumberFormat('en-IE', { style: 'currency', currency: 'EUR' });

/** "€50.00" for 5000 cents. */
export function formatCents(cents: number): string {
  return euro.format(cents / 100);
}

/** The most the API takes for a price (`priceCents` of `PlanRequest`), in cents. */
export const MAX_PRICE_CENTS = 100_000_000;

const AMOUNT = /^(\d{1,7})(?:[.,](\d{1,2}))?$/;

/**
 * Euros typed by a person, as exact cents: whole euros, then a point or a comma and one or two decimals. The digits are read
 * as text and never multiplied as floating point (0.1 * 100 is 10.000000000000002), so what is typed is what is stored.
 * Anything else (signs, exponents, a third decimal, thousands separators, symbols) is refused, as is more than the API accepts.
 */
export function parseEuros(text: string): number | null {
  const match = AMOUNT.exec(text.trim());
  if (!match) return null;
  const euros = Number(match[1]);
  const decimals = Number((match[2] ?? '').padEnd(2, '0'));
  const cents = euros * 100 + decimals;
  return cents <= MAX_PRICE_CENTS ? cents : null;
}

/** "19.99" for 1999 cents: the amount as an input holds it (two decimals, no symbol, no thousands separator). */
export function centsToInput(cents: number): string {
  const euros = Math.floor(cents / 100);
  const rest = cents % 100;
  return `${String(euros)}.${String(rest).padStart(2, '0')}`;
}
