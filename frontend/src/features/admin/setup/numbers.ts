/** A whole number typed in a field, within the API's limits; `null` for anything else (empty, decimals, signs, text). */
export function wholeNumber(text: string, min: number, max: number): number | null {
  const trimmed = text.trim();
  if (!/^\d{1,9}$/.test(trimmed)) return null;
  const value = Number(trimmed);
  return value >= min && value <= max ? value : null;
}

/** "Enter a whole number from 1 to 7." */
export const wholeNumberHint = (min: number, max: number) =>
  `Enter a whole number from ${String(min)} to ${String(max)}.`;
