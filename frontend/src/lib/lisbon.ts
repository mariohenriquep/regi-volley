/**
 * Everything the app shows about time is Europe/Lisbon wall-clock time, whatever the device's time zone (docs/requirements.md,
 * NFR "Fuso horário"). The API sends UTC instants; they are formatted here with an explicit `timeZone`, never with the
 * device's. A calendar day is an ISO `YYYY-MM-DD` string: day arithmetic is done on UTC dates at midday, so it cannot be
 * moved by the device's time zone or by a Lisbon clock change.
 */
const ZONE = 'Europe/Lisbon';

type Part = Intl.DateTimeFormatPartTypes;

function parts(formatter: Intl.DateTimeFormat, instant: Date): Partial<Record<Part, string>> {
  const result: Partial<Record<Part, string>> = {};
  for (const part of formatter.formatToParts(instant)) result[part.type] = part.value;
  return result;
}

const calendar = new Intl.DateTimeFormat('en-GB', {
  timeZone: ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});
const clock = new Intl.DateTimeFormat('en-GB', {
  timeZone: ZONE,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});
const dayAndTime = new Intl.DateTimeFormat('en-GB', {
  timeZone: ZONE,
  weekday: 'short',
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});
const heading = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'UTC',
  weekday: 'long',
  day: 'numeric',
  month: 'long',
});

/** The Lisbon calendar day (`YYYY-MM-DD`) of an instant. */
export function lisbonDay(instant: string | Date): string {
  const { year, month, day } = parts(calendar, new Date(instant));
  return `${year ?? ''}-${month ?? ''}-${day ?? ''}`;
}

/** The Lisbon wall-clock time (`HH:mm`, 24 h) of an instant. */
export function formatTime(instant: string | Date): string {
  const { hour, minute } = parts(clock, new Date(instant));
  return `${hour ?? ''}:${minute ?? ''}`;
}

/** "Tue 14 Jul, 20:30" in Lisbon time. */
export function formatDateTime(instant: string | Date): string {
  const { weekday, day, month, hour, minute } = parts(dayAndTime, new Date(instant));
  return `${weekday ?? ''} ${day ?? ''} ${month ?? ''}, ${hour ?? ''}:${minute ?? ''}`;
}

function atMidday(day: string): Date {
  return new Date(`${day}T12:00:00Z`);
}

/** "Tuesday 14 July" for a calendar day. */
export function dayHeading(day: string): string {
  const { weekday, day: dayOfMonth, month } = parts(heading, atMidday(day));
  return `${weekday ?? ''} ${dayOfMonth ?? ''} ${month ?? ''}`;
}

/** Calendar-day arithmetic (no clock changes involved). */
export function addDays(day: string, days: number): string {
  const moved = atMidday(day);
  moved.setUTCDate(moved.getUTCDate() + days);
  return moved.toISOString().slice(0, 10);
}

/** The Monday of the week a calendar day belongs to (weeks run Monday to Sunday, as the API's do). */
export function mondayOf(day: string): string {
  const sinceMonday = (atMidday(day).getUTCDay() + 6) % 7;
  return addDays(day, -sinceMonday);
}

export function durationMinutes(startsAt: string, endsAt: string): number {
  return Math.round((new Date(endsAt).getTime() - new Date(startsAt).getTime()) / 60_000);
}

/** "13 – 19 July", or "29 June – 5 July" when the week crosses a month, for the week starting on `monday`. */
export function weekRange(monday: string): string {
  const first = parts(heading, atMidday(monday));
  const last = parts(heading, atMidday(addDays(monday, 6)));
  return first.month === last.month
    ? `${first.day ?? ''} – ${last.day ?? ''} ${last.month ?? ''}`
    : `${first.day ?? ''} ${first.month ?? ''} – ${last.day ?? ''} ${last.month ?? ''}`;
}

const longDay = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'UTC',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
});

/** "1 July 2026" for a calendar day (a subscription's start or end date). */
export function longDate(day: string): string {
  const { day: dayOfMonth, month, year } = parts(longDay, atMidday(day));
  return `${dayOfMonth ?? ''} ${month ?? ''} ${year ?? ''}`;
}
