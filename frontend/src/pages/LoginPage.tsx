import { useMutation } from '@tanstack/react-query';
import { type SubmitEvent, useState } from 'react';
import { ErrorAlert } from '@/components/ErrorAlert';
import { useAuth } from '@/auth/context';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

/** Sign in. On success the PublicOnly guard sends the user where they were heading. */
export function LoginPage() {
  const { login, logout, logoutFailed } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const signIn = useMutation({ mutationFn: () => login(email, password) });

  const onSubmit = (event: SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    signIn.mutate();
  };

  return (
    <div className="grid gap-4">
      {logoutFailed && (
        <Alert variant="destructive">
          <AlertDescription>
            <p>Could not sign out on the server — try again.</p>
            <Button
              variant="outline"
              size="sm"
              className="mt-2"
              onClick={() => {
                void logout();
              }}
            >
              Try again
            </Button>
          </AlertDescription>
        </Alert>
      )}
      <Card>
        <CardHeader>
          <CardTitle>
            <h1 className="text-xl font-semibold">Sign in</h1>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <form className="grid gap-4" onSubmit={onSubmit} noValidate>
            <div className="grid gap-2">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                autoComplete="username"
                required
                value={email}
                onChange={(event) => {
                  setEmail(event.target.value);
                }}
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="password">Password</Label>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(event) => {
                  setPassword(event.target.value);
                }}
              />
            </div>
            {signIn.error && <ErrorAlert error={signIn.error} />}
            <Button type="submit" disabled={signIn.isPending}>
              {signIn.isPending ? 'Signing in…' : 'Sign in'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
