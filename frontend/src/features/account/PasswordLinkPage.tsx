import { useMutation } from '@tanstack/react-query';
import { type ReactNode, type SubmitEvent, useEffect, useState } from 'react';
import { Link, useLoaderData, useLocation, useNavigate } from 'react-router';
import { ApiError, unwrap } from '@/api/errors';
import { useAuth } from '@/auth/context';
import { Confirmation } from '@/components/Confirmation';
import { ErrorSummary } from '@/components/form/ErrorSummary';
import { type FormProblems, fromApiError, fromClient } from '@/components/form/problems';
import { TextField } from '@/components/form/TextField';
import { Button } from '@/components/ui/button';
import { takeFragmentToken, tokenFromHash } from '@/lib/fragment';
import { usePageTitle } from '@/lib/usePageTitle';
import { type AccountLinkData, createTokenHolder, type TokenHolder } from '@/pages/accountLink';
import type { ApiClient } from '@/auth/session';

export interface PasswordLinkConfig {
  title: string;
  intro: string;
  submitLabel: string;
  pendingLabel: string;
  doneTitle: string;
  doneText: string;
  /** What to do with a link that does not work. */
  deadLinkHelp: ReactNode;
  /** POSTs the single-use token and the new password to the API. */
  send: (api: ApiClient, token: string, password: string) => Promise<unknown>;
}

/** The fragment of an address, or '' when it is not one (an event without a URL). */
function hashOf(url: string): string {
  try {
    return new URL(url).hash;
  } catch {
    return '';
  }
}

const LABELS = { password: 'New password', confirm: 'Confirm new password' };
const FIELD_MAP = { password: 'password' };
/** The API's request limit (`maxLength` of `password`); its own policy (10 to 128 characters) decides the rest. */
const MAX_PASSWORD = 1000;

function DeadLink({
  incomplete,
  help,
  title,
}: {
  incomplete: boolean;
  help: ReactNode;
  title: string;
}) {
  return (
    <section className="grid gap-4">
      <h1 className="text-2xl font-semibold">{title}</h1>
      <p>
        {incomplete
          ? 'This link is incomplete. Open it again from the email, in full.'
          : 'This link is invalid or has expired.'}
      </p>
      <p>{help}</p>
    </section>
  );
}

/**
 * Sets a password with the single-use token from an emailed link (D-11): `/activate` and `/reset-password`. The route
 * loader read the token from the fragment, scrubbed it from the address bar and left it in a one-shot holder. The form
 * takes it once into its own state: it is sent in the body of the POST only (never in a URL or a query key, never
 * rendered) and is dropped once the API accepted it or refused the link.
 *
 * A router that starts from `/activate#token=...` keeps that fragment in its own location, so the page replaces it
 * with the bare path (a hash removal runs no loader).
 */
export function PasswordLinkPage({ config }: { config: PasswordLinkConfig }) {
  const { link: loaded } = useLoaderData<AccountLinkData>();
  const [newer, setNewer] = useState<{ link: TokenHolder; opened: number } | null>(null);
  const { hash, pathname, search } = useLocation();
  const navigate = useNavigate();
  useEffect(() => {
    if (hash) void navigate({ pathname, search }, { replace: true });
  }, [hash, pathname, search, navigate]);

  // Every link opened starts the form over, even the same one again: nothing is left from the previous.
  return (
    <PasswordLinkForm
      key={newer?.opened ?? 0}
      link={newer?.link ?? loaded}
      onNewLink={(link) => {
        setNewer((current) => ({ link, opened: (current?.opened ?? 0) + 1 }));
      }}
      config={config}
    />
  );
}

function PasswordLinkForm({
  link,
  onNewLink,
  config,
}: {
  link: TokenHolder;
  /** A second emailed link was opened in this tab: start over with it. */
  onNewLink: (link: TokenHolder) => void;
  config: PasswordLinkConfig;
}) {
  const { publicApi } = useAuth();
  usePageTitle(config.title);

  const [token, setToken] = useState(() => link.take());
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [problems, setProblems] = useState<FormProblems | null>(null);
  const [linkIsDead, setLinkIsDead] = useState(false);

  const send = useMutation({
    mutationFn: () => {
      if (!token) throw new Error('The link carries no token');
      return config.send(publicApi, token, password);
    },
    onSuccess: () => {
      setToken(null);
      setPassword('');
      setConfirm('');
    },
    onError: (error) => {
      if (error instanceof ApiError && error.code === 'INVALID_LINK') {
        setToken(null);
        setPassword('');
        setConfirm('');
        setLinkIsDead(true);
        return;
      }
      setProblems(fromApiError(error, FIELD_MAP));
    },
  });

  // A second emailed link opened in this tab changes the fragment only: the browser fires `hashchange` and runs no
  // loader. The address is scrubbed either way. While a password is being sent the new link is ignored, so the result
  // of that attempt is never replaced under the user's eyes; they open the link again afterwards.
  const sending = send.isPending;
  useEffect(() => {
    const onHashChange = (event: HashChangeEvent) => {
      // The event carries the new address: the router may have replaced it with the bare path already (it reacts to
      // the same navigation), so the address bar is scrubbed but is not the only place the token is read from.
      const fromEvent = tokenFromHash(hashOf(event.newURL));
      const next = fromEvent ?? takeFragmentToken();
      takeFragmentToken();
      if (next && !sending) onNewLink(createTokenHolder(next));
    };
    window.addEventListener('hashchange', onHashChange);
    return () => {
      window.removeEventListener('hashchange', onHashChange);
    };
  }, [sending, onNewLink]);

  if (send.isSuccess) {
    return (
      <Confirmation title={config.doneTitle}>
        <p>{config.doneText}</p>
        <Link to="/login" className="w-fit text-primary underline underline-offset-4">
          Sign in
        </Link>
      </Confirmation>
    );
  }
  if (linkIsDead || !token) {
    return <DeadLink incomplete={!linkIsDead} help={config.deadLinkHelp} title={config.title} />;
  }

  const onSubmit = (event: SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!password) {
      setProblems(fromClient({ password: 'Enter a new password' }));
    } else if (password !== confirm) {
      setProblems(fromClient({ confirm: 'The passwords do not match' }));
    } else {
      setProblems(null);
      send.mutate();
    }
  };

  return (
    <section className="grid gap-4">
      <h1 className="text-2xl font-semibold">{config.title}</h1>
      <p>{config.intro}</p>
      <form className="grid gap-4" method="post" onSubmit={onSubmit} noValidate>
        <ErrorSummary problems={problems} labels={LABELS} />
        <TextField
          id="password"
          label="New password"
          type="password"
          autoComplete="new-password"
          maxLength={MAX_PASSWORD}
          required
          hint="10 to 128 characters. A phrase you do not use anywhere else works well."
          value={password}
          onChange={(event) => {
            setPassword(event.target.value);
          }}
          error={problems?.fields.password}
        />
        <TextField
          id="confirm"
          label="Confirm new password"
          type="password"
          autoComplete="new-password"
          maxLength={MAX_PASSWORD}
          required
          value={confirm}
          onChange={(event) => {
            setConfirm(event.target.value);
          }}
          error={problems?.fields.confirm}
        />
        <Button type="submit" size="lg" className="h-11" disabled={send.isPending}>
          {send.isPending ? config.pendingLabel : config.submitLabel}
        </Button>
      </form>
    </section>
  );
}

export function ActivatePage() {
  return (
    <PasswordLinkPage
      config={{
        title: 'Activate your account',
        intro: 'Choose a password to finish activating your account.',
        submitLabel: 'Activate account',
        pendingLabel: 'Activating…',
        doneTitle: 'Account activated',
        doneText:
          'Your account is ready. Sign in with your email address and the password you just chose.',
        deadLinkHelp:
          'Activation links work once and expire after a few days. Ask your association to resend it.',
        send: (api, token, password) =>
          unwrap(api.POST('/api/v1/auth/activate', { body: { token, password } })),
      }}
    />
  );
}

export function ResetPasswordPage() {
  return (
    <PasswordLinkPage
      config={{
        title: 'Choose a new password',
        intro: 'Choose a new password for your account.',
        submitLabel: 'Change password',
        pendingLabel: 'Changing…',
        doneTitle: 'Password changed',
        doneText: 'Sign in with your new password. Other devices were signed out.',
        deadLinkHelp: (
          <>
            Reset links work once and expire after 30 minutes.{' '}
            <Link to="/forgot-password" className="text-primary underline underline-offset-4">
              Request a new link
            </Link>
          </>
        ),
        send: (api, token, password) =>
          unwrap(api.POST('/api/v1/auth/password-resets', { body: { token, password } })),
      }}
    />
  );
}
