import { useMutation } from '@tanstack/react-query';
import { useEffect } from 'react';
import {
  CalendarDaysIcon,
  ClipboardCheckIcon,
  HistoryIcon,
  ShieldCheckIcon,
  TicketIcon,
  UserRoundIcon,
} from 'lucide-react';
import { Link, NavLink, Outlet } from 'react-router';
import { useAuth } from '@/auth/context';
import { ErrorAlert } from '@/components/ErrorAlert';
import { useIsAdmin } from '@/features/admin/useIsAdmin';
import { useIsStaff } from '@/features/coaching/useIsStaff';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { cn } from '@/lib/utils';
import { UpdatePrompt } from '@/pwa/UpdatePrompt';

const navLink = 'rounded-sm px-1 py-1 underline-offset-4 hover:underline';

const SECTIONS = [
  { to: '/', label: 'Sessions', icon: CalendarDaysIcon, end: true },
  { to: '/me/plan', label: 'My plan', icon: TicketIcon, end: false },
  { to: '/me/history', label: 'History', icon: HistoryIcon, end: false },
] as const;

/** Only for coaches and administrators: shown when `GET /me` lists one of those roles (see `useIsStaff`). */
const COACHING = {
  to: '/coaching',
  label: 'Coaching',
  icon: ClipboardCheckIcon,
  end: false,
} as const;

/** Only for administrators: shown when `GET /me` lists ADMIN (see `useIsAdmin`). */
const ADMIN = {
  to: '/admin',
  label: 'Admin',
  icon: ShieldCheckIcon,
  end: false,
} as const;

/** Tailwind needs the whole class name in the source, so each bar size is spelled out. */
const BAR_COLUMNS: Record<number, string> = {
  3: 'grid-cols-3',
  4: 'grid-cols-4',
  5: 'grid-cols-5',
};

export function AppLayout() {
  const { status, logout, logoutAll } = useAuth();
  const signOutEverywhere = useMutation({ mutationFn: logoutAll });
  const signedIn = status === 'authenticated';
  const { settled, staff } = useIsStaff();
  const { admin } = useIsAdmin();
  const sections = [...SECTIONS, ...(staff ? [COACHING] : []), ...(admin ? [ADMIN] : [])];
  const { reset: clearSignOutError } = signOutEverywhere;
  // A failure belongs to the session it happened in: forget it when the user changes (sign-out, sign-in, session end).
  useEffect(() => {
    clearSignOutError();
  }, [status, clearSignOutError]);
  return (
    <div className="flex min-h-dvh flex-col">
      <header className="flex items-center justify-between gap-3 border-b bg-primary px-4 py-3 text-primary-foreground">
        <Link to="/" className="text-lg font-semibold">
          RegiVolley
        </Link>
        <nav aria-label="Main" className="flex items-center gap-3 text-sm">
          {signedIn && (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="secondary" size="sm" className="h-9 px-3">
                  <UserRoundIcon aria-hidden="true" />
                  Account
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent>
                <DropdownMenuItem
                  onSelect={() => {
                    void logout();
                  }}
                >
                  Sign out
                </DropdownMenuItem>
                <DropdownMenuItem
                  disabled={signOutEverywhere.isPending}
                  onSelect={() => {
                    signOutEverywhere.mutate();
                  }}
                >
                  Sign out on all devices
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
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
      {signedIn && signOutEverywhere.error && (
        <div className="mx-auto w-full max-w-md px-4 pt-4">
          <ErrorAlert error={signOutEverywhere.error} />
        </div>
      )}
      <main className={cn('mx-auto w-full max-w-md flex-1 p-4', signedIn && 'pb-28')}>
        <Outlet />
      </main>
      <footer className={cn('mx-auto w-full max-w-md px-4 pb-6 text-sm', signedIn && 'pb-24')}>
        <Link to="/privacy" className="text-primary underline underline-offset-4">
          Privacy policy
        </Link>
      </footer>
      {signedIn && settled && (
        <nav
          aria-label="Sections"
          className={cn(
            'fixed inset-x-0 bottom-0 z-40 grid border-t bg-background pb-[env(safe-area-inset-bottom)]',
            BAR_COLUMNS[sections.length],
          )}
        >
          {sections.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                cn(
                  'flex min-h-14 flex-col items-center justify-center gap-0.5 text-xs font-medium outline-none focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring/50',
                  isActive ? 'text-primary' : 'text-muted-foreground',
                )
              }
            >
              <Icon className="size-5" aria-hidden="true" />
              {label}
            </NavLink>
          ))}
        </nav>
      )}
    </div>
  );
}
