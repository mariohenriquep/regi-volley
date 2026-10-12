import { useLoaderData } from 'react-router';
import type { AccountLinkData } from './accountLink';

/**
 * Stub of the two account-link pages (F2 adds the password form that POSTs the token to /auth/activate or
 * /auth/password-resets). Loads no third-party resource; the token is never shown.
 */
export function AccountLinkPage({ title }: { title: string }) {
  const { token } = useLoaderData<AccountLinkData>();
  return (
    <section className="grid gap-4">
      <h1 className="text-2xl font-semibold">{title}</h1>
      <p>
        {token
          ? 'Your link is ready. Setting your password here arrives in the next version.'
          : 'This link is incomplete. Open it again from the email, or ask for a new one.'}
      </p>
    </section>
  );
}
