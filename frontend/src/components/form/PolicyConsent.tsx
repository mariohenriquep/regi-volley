import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { POLICY_VERSION } from '@/lib/privacy';

/**
 * The RGPD consent checkbox (US-01, US-05): names the policy version being accepted. The policy opens in a new tab, so
 * nothing typed in the form is lost.
 */
export function PolicyConsent({
  id,
  checked,
  onChange,
  error,
  purpose,
}: {
  id: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  error?: string;
  /** What the data is used for, in one phrase. */
  purpose: string;
}) {
  const errorId = `${id}-error`;
  return (
    <div className="grid gap-2">
      <div className="flex items-start gap-3">
        <Checkbox
          id={id}
          checked={checked}
          onCheckedChange={(next) => {
            onChange(next === true);
          }}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? errorId : undefined}
          className="mt-0.5"
        />
        <Label htmlFor={id} className="block text-sm leading-snug font-normal">
          I agree that my personal data is processed {purpose}, as described in the{' '}
          <a
            href="/privacy"
            target="_blank"
            rel="noopener"
            className="text-primary underline underline-offset-4"
          >
            privacy policy
          </a>{' '}
          (version {POLICY_VERSION}).
        </Label>
      </div>
      {error && (
        <p id={errorId} className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}
