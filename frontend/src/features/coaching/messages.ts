import { plural } from './model';
import type { AttendanceSaved, CapacityChanged, SessionCancelled } from './useCoachingMutations';

/** What the API did when a session was cancelled, in its numbers. */
export function cancelledNotice({ bookingsCancelled, creditsRefunded }: SessionCancelled): string {
  const bookings = plural(bookingsCancelled, 'booking was', 'bookings were');
  const credits = plural(creditsRefunded, 'credit', 'credits');
  return `Session cancelled. ${bookings} cancelled and ${credits} refunded.`;
}

/** What the API did when the seats changed: the new number and how many people moved up from the waitlist. */
export function capacityNotice({ capacity, promotedCount }: CapacityChanged): string {
  const head = `Seats changed to ${String(capacity)}.`;
  if (promotedCount === 0) return `${head} Nobody moved up.`;
  return `${head} ${promotedCount === 1 ? '1 person moved' : `${String(promotedCount)} people moved`} up from the waitlist.`;
}

/** What was saved, and how many members this batch brought to the no-show limit. */
export function attendanceNotice({ attended, noShow, warned }: AttendanceSaved): string {
  const head = `Attendance saved: ${String(attended)} attended, ${String(noShow)} no-show.`;
  if (warned === 0) return head;
  return `${head} ${plural(warned, 'member reached', 'members reached')} the no-show limit and ${warned === 1 ? 'was' : 'were'} warned.`;
}

/** The warning above the reason: who will be told. Waitlisted members are told as well. */
export function cancellationWarning(bookedCount: number): string {
  if (bookedCount === 0) return 'Nobody has booked this session, so no one will be notified.';
  return bookedCount === 1
    ? '1 member will be notified and their credit refunded.'
    : `${String(bookedCount)} members will be notified and their credits refunded.`;
}
