import { useMutation, useQuery } from '@tanstack/react-query';
import { unwrap } from '@/api/errors';
import { useAuth } from '@/auth/context';
import { ErrorAlert } from '@/components/ErrorAlert';
import { Loading } from '@/components/Loading';
import { Button } from '@/components/ui/button';

/** Placeholder home (F1): proves an authenticated call through the typed client. F3 replaces it with the week. */
export function HomePage() {
  const { api, logoutAll } = useAuth();
  const me = useQuery({ queryKey: ['me'], queryFn: () => unwrap(api.GET('/api/v1/me')) });
  const signOutEverywhere = useMutation({ mutationFn: logoutAll });

  return (
    <section className="grid gap-4">
      <h1 className="text-2xl font-semibold">Welcome back</h1>
      {me.isPending && <Loading />}
      {me.error && <ErrorAlert error={me.error} />}
      {me.data && (
        <dl className="grid gap-1 text-sm">
          <dt className="text-muted-foreground">Member ID</dt>
          <dd className="font-mono break-all">{me.data.memberId}</dd>
        </dl>
      )}
      {signOutEverywhere.error && <ErrorAlert error={signOutEverywhere.error} />}
      <Button
        variant="outline"
        disabled={signOutEverywhere.isPending}
        onClick={() => {
          signOutEverywhere.mutate();
        }}
      >
        Sign out on all devices
      </Button>
    </section>
  );
}
