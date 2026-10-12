/**
 * The API's dates are ISO-8601: instants (`2026-07-14T19:30:00Z`) and calendar days (`2026-07-14`). Both come from a
 * contract the generated types only describe as `string`, so a response is checked before it is formatted: an unreadable
 * date would show as "Invalid Date" or throw inside `Intl`.
 */
export function isInstant(value: unknown): value is string {
  return typeof value === 'string' && value.includes('T') && !Number.isNaN(Date.parse(value));
}

/** A real calendar day, `YYYY-MM-DD` (February 30th is not one). */
export function isCalendarDay(value: unknown): value is string {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().startsWith(value);
}
