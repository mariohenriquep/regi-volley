import { Link, Outlet } from 'react-router';
import { useAuth } from '@/auth/context';
import { Button } from '@/components/ui/button';
import { UpdatePrompt } from '@/pwa/UpdatePrompt';

const navLink = 'rounded-sm px-1 py-1 underline-offset-4 hover:underline';

export function AppLayout() {
  const { status, logout } = useAuth();
  return (
    <div className="flex min-h-dvh flex-col">
      <header className="flex items-center justify-between gap-3 border-b bg-primary px-4 py-3 text-primary-foreground">
        <Link to="/" className="text-lg font-semibold">
          RegiVolley
        </Link>
        <nav aria-label="Main" className="flex items-center gap-3 text-sm">
          {status === 'authenticated' && (
            <Button variant="secondary" size="sm" onClick={() => void logout()}>
              Sign out
            </Button>
          )}
          {status === 'anonymous' && (
            <>
              <Link to="/login" className={navLink}>
                Sign in
              </Link>
              <Link to="/register" className={navLink}>
                Register an association
              </Link>
            </>
          )}
        </nav>
      </header>
      <UpdatePrompt />
      <main className="mx-auto w-full max-w-md flex-1 p-4">
        <Outlet />
      </main>
      <footer className="mx-auto w-full max-w-md px-4 pb-6 text-sm">
        <Link to="/privacy" className="text-primary underline underline-offset-4">
          Privacy policy
        </Link>
      </footer>
    </div>
  );
}
