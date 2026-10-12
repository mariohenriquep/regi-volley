import { useMutation } from '@tanstack/react-query';
import { type SubmitEvent, useState } from 'react';
import { Link } from 'react-router';
import { unwrap } from '@/api/errors';
import { useAuth } from '@/auth/context';
import { Confirmation } from '@/components/Confirmation';
import { ErrorSummary } from '@/components/form/ErrorSummary';
import { type FormProblems, fromApiError, fromClient } from '@/components/form/problems';
import { TextField } from '@/components/form/TextField';
import { Button } from '@/components/ui/button';
import { EMAIL_SHAPE } from '@/lib/email';
import { usePageTitle } from '@/lib/usePageTitle';

const LABELS = { email: 'Email' };
const FIELD_MAP = { email: 'email' };

/**
 * Asks for a password reset link. A 202 always shows the same confirmation, whether or not the address has an account
 * (threat model D-10): the page never says which.
 */
export function ForgotPasswordPage() {
  const { publicApi } = useAuth();
  usePageTitle('Forgot password');
  const [email, setEmail] = useState('');
  const [problems, setProblems] = useState<FormProblems | null>(null);

  const request = useMutation({
    mutationFn: () =>
      unwrap(
        publicApi.POST('/api/v1/auth/password-reset-requests', { body: { email: email.trim() } }),
      ),
    onError: (error) => {
      setProblems(fromApiError(error, FIELD_MAP));
    },
  });

  if (request.isSuccess) {
    return (
      <Confirmation title="Check your email">
        <p>If an account exists for this email, we have sent a link to reset the password.</p>
        <p>The link works once and expires after 30 minutes.</p>
        <Link to="/login" className="w-fit text-primary underline underline-offset-4">
          Back to sign in
        </Link>
      </Confirmation>
    );
  }

  const onSubmit = (event: SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!EMAIL_SHAPE.test(email.trim())) {
      setProblems(fromClient({ email: 'Enter a valid email address' }));
      return;
    }
    setProblems(null);
    request.mutate();
  };

  return (
    <section className="grid gap-4">
      <h1 className="text-2xl font-semibold">Forgot your password?</h1>
      <p>Enter your email address and we will send you a link to choose a new one.</p>
      <form className="grid gap-4" method="post" onSubmit={onSubmit} noValidate>
        <ErrorSummary problems={problems} labels={LABELS} />
        <TextField
          id="email"
          label="Email"
          type="email"
          autoComplete="username"
          maxLength={254}
          required
          value={email}
          onChange={(event) => {
            setEmail(event.target.value);
          }}
          error={problems?.fields.email}
        />
        <Button type="submit" size="lg" className="h-11" disabled={request.isPending}>
          {request.isPending ? 'Sending…' : 'Send reset link'}
        </Button>
        <Link to="/login" className="w-fit text-sm text-primary underline underline-offset-4">
          Back to sign in
        </Link>
      </form>
    </section>
  );
}
