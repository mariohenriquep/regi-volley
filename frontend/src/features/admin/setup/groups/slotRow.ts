/** One row of the schedule being edited. `key` is stable (rows are added and removed anywhere), the rest is what was typed. */
export interface SlotRow {
  key: number;
  day: string;
  time: string;
  minutes: string;
}

/** The id of one control of a slot row; the error summary and the field errors find the control by it. */
export const slotControl = (row: SlotRow, field: 'day' | 'time' | 'minutes') =>
  `slot-${String(row.key)}-${field}`;
