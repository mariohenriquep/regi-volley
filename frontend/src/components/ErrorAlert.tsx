import { ApiError, GENERIC_ERROR_MESSAGE } from '@/api/errors';
import { Alert, AlertDescription } from '@/components/ui/alert';

/** Shows an error the way the API contract allows: its safe message, plus the request id for support. */
export function ErrorAlert({ error }: { error: unknown }) {
  const apiError = error instanceof ApiError ? error : null;
  return (
    <Alert variant="destructive">
      <AlertDescription>
        <p>{apiError?.message ?? GENERIC_ERROR_MESSAGE}</p>
        {apiError?.requestId && <p className="text-xs">Reference: {apiError.requestId}</p>}
      </AlertDescription>
    </Alert>
  );
}
