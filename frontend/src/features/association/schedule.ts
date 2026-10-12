const DAYS: Record<string, string> = {
  MONDAY: 'Monday',
  TUESDAY: 'Tuesday',
  WEDNESDAY: 'Wednesday',
  THURSDAY: 'Thursday',
  FRIDAY: 'Friday',
  SATURDAY: 'Saturday',
  SUNDAY: 'Sunday',
};

/** "90" becomes "1 h 30 min". */
export function durationText(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours === 0) return `${String(rest)} min`;
  return rest === 0 ? `${String(hours)} h` : `${String(hours)} h ${String(rest)} min`;
}

/** One weekly slot as the API gives it (day name, Lisbon `HH:mm`, minutes): "Tuesday 20:30, 1 h 30 min". */
export function slotText(slot: {
  dayOfWeek?: string;
  startTime?: string;
  durationMinutes?: number;
}): string {
  const day = DAYS[slot.dayOfWeek ?? ''] ?? slot.dayOfWeek ?? '';
  const time = slot.startTime ?? '';
  const duration =
    slot.durationMinutes === undefined ? '' : `, ${durationText(slot.durationMinutes)}`;
  return `${day} ${time}${duration}`.trim();
}
