import { ApiError, GENERIC_ERROR_MESSAGE } from '@/api/errors';

/**
 * What a form shows after a failed attempt, whether found in the browser or answered by the API: messages attached
 * to controls (by the control's id) and general ones that belong to no control.
 */
export interface FormProblems {
  fields: Record<string, string>;
  general: string[];
  /** The API's request id, for support. */
  requestId?: string;
}

/** Control ids by {@link normaliseFieldName normalised} API field name: each form lists its own. */
export type FieldMap = Record<string, string>;

/**
 * The API names the same field two ways: the 400 validation uses the request property (`shortName`, `levelNames[0]`),
 * a 422 rule the human label (`short name`, `NIF`). Both meet in one lower-case key without spaces or indexes.
 */
export function normaliseFieldName(name: string): string {
  return name
    .replace(/\[\d+\]/g, '')
    .replace(/[^a-z0-9]/gi, '')
    .toLowerCase();
}

/** "15 minutes": how long a 429's `Retry-After` asks the user to wait (rounded up, never zero). */
export function waitText(seconds: number | undefined): string {
  if (seconds === undefined || seconds <= 0) return 'a little while';
  const plural = (count: number, unit: string) =>
    `${String(count)} ${unit}${count === 1 ? '' : 's'}`;
  if (seconds < 60) return plural(seconds, 'second');
  if (seconds < 3600) return plural(Math.ceil(seconds / 60), 'minute');
  return plural(Math.ceil(seconds / 3600), 'hour');
}

/** What a 429 says: the API's `Retry-After`, in words. */
export const rateLimitText = (seconds: number | undefined) =>
  `Too many attempts. Please wait ${waitText(seconds)} and try again.`;

export const FIELD_NOT_VALID = 'Check this field.';

export function fromClient(fields: Record<string, string>): FormProblems {
  return { fields, general: [] };
}

/**
 * Reads a failed call the way the contract allows: a 422 shows the API's English message on the control it names; a 400
 * only names fields (its message is a generic "Invalid request"); a 429 asks for patience; a 5xx or anything that is not
 * the API's shape is generic text. A field the form has no control for goes to the general messages.
 */
export function fromApiError(error: unknown, fieldMap: FieldMap): FormProblems {
  if (!(error instanceof ApiError)) return { fields: {}, general: [GENERIC_ERROR_MESSAGE] };
  const problems: FormProblems = { fields: {}, general: [], requestId: error.requestId };

  if (error.status === 429) {
    problems.general.push(rateLimitText(error.retryAfterSeconds));
    return problems;
  }

  const named = error.status === 400 || error.status === 422;
  if (named && error.fields.length > 0) {
    for (const field of error.fields) {
      const control = fieldMap[normaliseFieldName(field)];
      if (control)
        problems.fields[control] = error.status === 422 ? error.message : FIELD_NOT_VALID;
      else if (error.status === 422) problems.general.push(error.message);
    }
    if (error.status === 400 && Object.keys(problems.fields).length === 0) {
      problems.general.push('Some of the information is not valid. Check it and try again.');
    }
    return problems;
  }

  problems.general.push(error.message);
  return problems;
}
