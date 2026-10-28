import { useMemo, useState } from 'react';
import {
  type FieldMap,
  type FormProblems,
  fromApiError,
  fromClient,
} from '@/components/form/problems';

/**
 * What a form shows after a failed attempt: the browser's own checks (`fail`) win over the API's answer to the last send
 * (`error`), and the object stays the same until something new happens, so the error summary takes focus once per attempt.
 */
export function useFormProblems(error: unknown, fieldMap: FieldMap) {
  const [client, setClient] = useState<FormProblems | null>(null);
  const fromApi = useMemo(
    () => (error == null ? null : fromApiError(error, fieldMap)),
    [error, fieldMap],
  );
  return {
    problems: client ?? fromApi,
    /** Fields the browser found wrong, by control id. */
    fail: (fields: Record<string, string>) => {
      setClient(fromClient(fields));
    },
    clear: () => {
      setClient(null);
    },
  };
}
