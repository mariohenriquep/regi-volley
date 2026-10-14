/**
 * The API states money in cents and names no currency. The product serves Portuguese associations (Lisbon time, PT-PT
 * requirements), so amounts are shown in euros; a second currency would need the API to say which.
 */
const euro = new Intl.NumberFormat('en-IE', { style: 'currency', currency: 'EUR' });

/** "€50.00" for 5000 cents. */
export function formatCents(cents: number): string {
  return euro.format(cents / 100);
}
