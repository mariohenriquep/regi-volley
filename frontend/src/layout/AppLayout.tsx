import { Link, Outlet } from 'react-router';
import { useAuth } from '@/auth/context';
import { Button } from '@/components/ui/button';

export function AppLayout() {
  const { status, logout } = useAuth();
  return (
    <div className="flex min-h-dvh flex-col">
      <header className="flex items-center justify-between border-b bg-primary px-4 py-3 text-primary-foreground">
        <Link to="/" className="text-lg font-semibold">
          RegiVolley
        </Link>
        {status === 'authenticated' && (
          <Button variant="secondary" size="sm" onClick={() => void logout()}>
            Sign out
          </Button>
        )}
      </header>
      <main className="mx-auto w-full max-w-md flex-1 p-4">
        <Outlet />
      </main>
    </div>
  );
}
