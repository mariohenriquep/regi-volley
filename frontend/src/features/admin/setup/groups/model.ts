import type { components } from '@/api/schema.gen';
import type { MemberListing } from '../../members/model';

type Schemas = components['schemas'];

/** The API's limits for a training group (`CreateTrainingGroupRequest`, `WeeklySlotRequest`). */
export const MAX_GROUP_NAME = 100;
export const SEATS = { min: 1, max: 1000 } as const;
export const SLOT_MINUTES = { min: 1, max: 240 } as const;
export const MAX_SLOTS = 14;

export const DAYS = [
  { value: 'MONDAY', label: 'Monday' },
  { value: 'TUESDAY', label: 'Tuesday' },
  { value: 'WEDNESDAY', label: 'Wednesday' },
  { value: 'THURSDAY', label: 'Thursday' },
  { value: 'FRIDAY', label: 'Friday' },
  { value: 'SATURDAY', label: 'Saturday' },
  { value: 'SUNDAY', label: 'Sunday' },
] as const;
export type Day = (typeof DAYS)[number]['value'];

export const isDay = (value: string | undefined): value is Day =>
  DAYS.some((day) => day.value === value);

export interface Named {
  id: string;
  name: string;
}

/** One weekly time of a group, in Europe/Lisbon wall-clock time. */
export interface Slot {
  /** A weekday of this version's list; any other word is shown as the API wrote it. */
  dayOfWeek: string;
  /** `HH:mm`. */
  startTime: string;
  durationMinutes: number;
}

export interface Group {
  id: string;
  name: string;
  /** `ACTIVE` or `ARCHIVED`; any other word is shown as the API wrote it. */
  status: string;
  acceptedLevels: Named[];
  venue: Named | undefined;
  coach: Named | undefined;
  capacity: number;
  schedule: Slot[];
}

export const isArchived = (group: Group) => group.status === 'ARCHIVED';

function named(raw: Schemas['NamedRefView'] | undefined): Named | undefined {
  return raw?.id && raw.name ? { id: raw.id, name: raw.name } : undefined;
}

/** "20:30" from "20:30" or "20:30:00": a slot is whole minutes. */
const clock = (time: string | undefined) => /^\d{2}:\d{2}/.exec(time ?? '')?.[0];

function toSlot(raw: Schemas['GroupEntrySlotView']): Slot | null {
  const startTime = clock(raw.startTime);
  if (!raw.dayOfWeek || !startTime || typeof raw.durationMinutes !== 'number') return null;
  return { dayOfWeek: raw.dayOfWeek, startTime, durationMinutes: raw.durationMinutes };
}

/** The objects of a payload that should be a list; anything else is an empty list. */
function objects(raw: unknown): object[] {
  return Array.isArray(raw)
    ? raw.filter((row: unknown): row is object => typeof row === 'object' && row !== null)
    : [];
}

export function toGroup(raw: Schemas['TrainingGroupEntryResponse']): Group | null {
  if (!raw.id) return null;
  return {
    id: raw.id,
    name: raw.name ?? '',
    status: raw.status ?? '',
    acceptedLevels: objects(raw.acceptedLevels).flatMap((level) => named(level) ?? []),
    venue: named(raw.venue),
    coach: named(raw.coach),
    capacity: raw.capacity ?? 0,
    schedule: objects(raw.schedule).flatMap((slot) => toSlot(slot) ?? []),
  };
}

/** The groups in the order the API gives them; a payload that is not a list is an empty one. */
export function toGroups(raw: unknown): Group[] {
  return objects(raw).flatMap((row) => toGroup(row) ?? []);
}

/** "Tuesday 20:30, 90 min". */
export function slotText(slot: Slot): string {
  const day = DAYS.find(({ value }) => value === slot.dayOfWeek)?.label ?? slot.dayOfWeek;
  return `${day} ${slot.startTime}, ${String(slot.durationMinutes)} min`;
}

/** The members who can coach: active and holding COACH. `incomplete` when the list was cut short, so one may be missing. */
export function toCoaches(listing: MemberListing): { coaches: Named[]; incomplete: boolean } {
  return {
    coaches: listing.members
      .filter((member) => member.status === 'ACTIVE' && member.roles.includes('COACH'))
      .map(({ id, name }) => ({ id, name })),
    incomplete: listing.truncated,
  };
}
