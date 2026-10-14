import { type SubmitEvent, useEffect, useRef, useState } from 'react';
import { ApiError } from '@/api/errors';
import { ErrorAlert } from '@/components/ErrorAlert';
import { FIELD_NOT_VALID } from '@/components/form/problems';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import type { JoinRequest } from './model';

/** The API's limit for a rejection reason (`RejectionRequest`). */
const MAX_REASON = 500;

/**
 * Rejects a join request (US-06) with an optional reason. The applicant is emailed that the request was declined; the
 * reason is kept with the request and is not in that email, and the dialog says so. It stays open until the API has
 * answered and shows a refusal where it happened.
 */
export function RejectDialog({
  request,
  pending,
  error,
  onReject,
  onClose,
  onCloseAutoFocus,
}: {
  request: JoinRequest | null;
  pending: boolean;
  error: unknown;
  onReject: (reason: string) => void;
  onClose: () => void;
  onCloseAutoFocus: (event: Event) => void;
}) {
  return (
    <Dialog
      open={request !== null}
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      {request && (
        <RejectSheet
          key={request.id}
          request={request}
          pending={pending}
          error={error}
          onReject={onReject}
          onClose={onClose}
          onCloseAutoFocus={onCloseAutoFocus}
        />
      )}
    </Dialog>
  );
}

function RejectSheet({
  request,
  pending,
  error,
  onReject,
  onClose,
  onCloseAutoFocus,
}: {
  request: JoinRequest;
  pending: boolean;
  error: unknown;
  onReject: (reason: string) => void;
  onClose: () => void;
  onCloseAutoFocus: (event: Event) => void;
}) {
  const [reason, setReason] = useState('');
  const field = useRef<HTMLTextAreaElement>(null);

  // The API names the reason itself (a 400 or 422 with `fields`): that is a field problem; anything else is the dialog's.
  const apiError = error instanceof ApiError ? error : null;
  const apiNamesReason = apiError?.fields.some((name) => name.toLowerCase().includes('reason'));
  const fieldError = apiNamesReason
    ? apiError?.status === 422
      ? apiError.message
      : FIELD_NOT_VALID
    : undefined;

  useEffect(() => {
    if (error && apiNamesReason) field.current?.focus();
  }, [error, apiNamesReason]);

  const submit = (event: SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    onReject(reason.trim());
  };

  return (
    <DialogContent onCloseAutoFocus={onCloseAutoFocus}>
      <form noValidate method="post" onSubmit={submit} className="grid gap-4">
        <DialogHeader>
          <DialogTitle>Reject {request.name}?</DialogTitle>
          <DialogDescription>
            They are emailed that the request was declined. The reason is kept with the request and
            is not included in the email.
          </DialogDescription>
        </DialogHeader>

        <div className="grid gap-2">
          <Label htmlFor="rejection-reason">Reason (optional)</Label>
          <Textarea
            id="rejection-reason"
            ref={field}
            value={reason}
            maxLength={MAX_REASON}
            aria-invalid={fieldError ? true : undefined}
            aria-describedby={fieldError ? 'rejection-reason-error' : undefined}
            onChange={(event) => {
              setReason(event.target.value);
            }}
          />
          {fieldError && (
            <p id="rejection-reason-error" className="text-sm text-destructive">
              {fieldError}
            </p>
          )}
        </div>

        {error != null && !apiNamesReason && <ErrorAlert error={error} />}

        <DialogFooter>
          <Button type="button" variant="outline" size="lg" className="h-11" onClick={onClose}>
            Not now
          </Button>
          <Button type="submit" variant="destructive" size="lg" className="h-11" disabled={pending}>
            {pending ? 'Rejecting…' : 'Reject request'}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  );
}
