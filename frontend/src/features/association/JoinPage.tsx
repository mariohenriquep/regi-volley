import { useMutation } from '@tanstack/react-query';
import { type SubmitEvent, useState } from 'react';
import { Link, useParams } from 'react-router';
import { ApiError, unwrap } from '@/api/errors';
import { useAuth } from '@/auth/context';
import { Confirmation } from '@/components/Confirmation';
import { ErrorSummary } from '@/components/form/ErrorSummary';
import { PolicyConsent } from '@/components/form/PolicyConsent';
import {
  type FieldMap,
  type FormProblems,
  fromApiError,
  fromClient,
} from '@/components/form/problems';
import { TextField } from '@/components/form/TextField';
import { Button } from '@/components/ui/button';
import { EMAIL_SHAPE } from '@/lib/email';
import { POLICY_VERSION } from '@/lib/privacy';
import { usePageTitle } from '@/lib/usePageTitle';
import { AssociationNotFound } from './AssociationNotFound';
import { usePublicAssociation } from './usePublicAssociation';

/** Limits of the API's request (docs/api/openapi.json): the inputs stop there, the API stays the authority. */
const MAX = { name: 100, email: 254, phone: 30 } as const;

const LABELS = { name: 'Name', email: 'Email', phone: 'Phone', consent: 'Consent' };
const FIELD_MAP: FieldMap = {
  name: 'name',
  email: 'email',
  phone: 'phone',
  consentaccepted: 'consent',
};

export const JOIN_CONFIRMATION =
  'If this email can join, the association will review your request.';

/**
 * Join request (US-05, "Want to try it?"). Whatever the API answers with a 202 (new request, already a member, request
 * pending) the page shows the same confirmation: it never hints at an existing membership (threat model D-14).
 */
export function JoinPage() {
  const { shortName = '' } = useParams();
  const { publicApi } = useAuth();
  const association = usePublicAssociation(shortName);
  usePageTitle('Request to join');

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [consent, setConsent] = useState(false);
  const [problems, setProblems] = useState<FormProblems | null>(null);

  const send = useMutation({
    mutationFn: () =>
      unwrap(
        publicApi.POST('/api/v1/public/associations/{shortName}/join-requests', {
          params: { path: { shortName } },
          body: {
            name: name.trim(),
            email: email.trim(),
            phone: phone.trim(),
            consentAccepted: consent,
            policyVersion: POLICY_VERSION,
          },
        }),
      ),
    onError: (error) => {
      setProblems(fromApiError(error, FIELD_MAP));
    },
  });

  if (
    (association.error instanceof ApiError && association.error.status === 404) ||
    (send.error instanceof ApiError && send.error.status === 404)
  ) {
    return <AssociationNotFound />;
  }

  const associationName = association.data?.name;
  const publicPage = `/a/${encodeURIComponent(shortName)}`;

  if (send.isSuccess) {
    return (
      <Confirmation title="Request received">
        <p>{JOIN_CONFIRMATION}</p>
        <Link to={publicPage} className="w-fit text-primary underline underline-offset-4">
          {associationName ? `Back to ${associationName}` : 'Back to the association page'}
        </Link>
      </Confirmation>
    );
  }

  const onSubmit = (event: SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    const missing: Record<string, string> = {};
    if (!name.trim()) missing.name = 'Enter your name';
    if (!email.trim()) missing.email = 'Enter your email address';
    else if (!EMAIL_SHAPE.test(email.trim())) missing.email = 'Enter a valid email address';
    if (!phone.trim()) missing.phone = 'Enter your phone number';
    if (!consent) missing.consent = 'Accept the privacy policy to send your request';
    if (Object.keys(missing).length > 0) {
      setProblems(fromClient(missing));
      return;
    }
    setProblems(null);
    send.mutate();
  };

  return (
    <section className="grid gap-4">
      <div className="grid gap-1">
        <h1 className="text-2xl font-semibold">Request to join</h1>
        {associationName && <p className="text-muted-foreground">{associationName}</p>}
        <p>Leave your details and the association will get in touch by email.</p>
      </div>
      <form className="grid gap-4" method="post" onSubmit={onSubmit} noValidate>
        <ErrorSummary problems={problems} labels={LABELS} />
        <TextField
          id="name"
          label="Name"
          autoComplete="name"
          maxLength={MAX.name}
          required
          value={name}
          onChange={(event) => {
            setName(event.target.value);
          }}
          error={problems?.fields.name}
        />
        <TextField
          id="email"
          label="Email"
          type="email"
          autoComplete="email"
          maxLength={MAX.email}
          required
          value={email}
          onChange={(event) => {
            setEmail(event.target.value);
          }}
          error={problems?.fields.email}
        />
        <TextField
          id="phone"
          label="Phone"
          type="tel"
          autoComplete="tel"
          maxLength={MAX.phone}
          required
          value={phone}
          onChange={(event) => {
            setPhone(event.target.value);
          }}
          error={problems?.fields.phone}
        />
        <PolicyConsent
          id="consent"
          checked={consent}
          onChange={setConsent}
          error={problems?.fields.consent}
          purpose="to review my request and contact me about it"
        />
        <Button type="submit" size="lg" className="h-11" disabled={send.isPending}>
          {send.isPending ? 'Sending…' : 'Send request'}
        </Button>
      </form>
    </section>
  );
}
