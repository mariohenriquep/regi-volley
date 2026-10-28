import {
  BuildingIcon,
  CalendarRangeIcon,
  ChevronRightIcon,
  LayersIcon,
  TicketIcon,
  UserCheckIcon,
  UsersIcon,
} from 'lucide-react';
import type { ComponentType } from 'react';
import { Link } from 'react-router';
import { Loading } from '@/components/Loading';
import { usePageTitle } from '@/lib/usePageTitle';
import { NotAdmin } from './NotAdmin';
import { useIsAdmin } from './useIsAdmin';

const SCREENS: {
  to: string;
  title: string;
  hint: string;
  icon: ComponentType<{ className?: string }>;
}[] = [
  {
    to: '/admin/join-requests',
    title: 'Join requests',
    hint: 'Approve or reject the people who asked to join.',
    icon: UserCheckIcon,
  },
  {
    to: '/admin/members',
    title: 'Members',
    hint: 'Search members, change levels and roles, deactivate.',
    icon: UsersIcon,
  },
  {
    to: '/admin/setup/levels',
    title: 'Levels',
    hint: 'Name and order the levels, choose the entry level.',
    icon: LayersIcon,
  },
  {
    to: '/admin/setup/venues',
    title: 'Venues',
    hint: 'Add the places where your groups play.',
    icon: BuildingIcon,
  },
  {
    to: '/admin/setup/groups',
    title: 'Training groups',
    hint: 'Weekly schedules, levels, seats and coaches.',
    icon: CalendarRangeIcon,
  },
  {
    to: '/admin/setup/plans',
    title: 'Plans',
    hint: 'What members buy: packs, monthly passes, single sessions.',
    icon: TicketIcon,
  },
];

/** The administrator's home: one link to each admin screen. Payments join the list in the next step. */
export function AdminPage() {
  usePageTitle('Admin');
  const { settled, admin } = useIsAdmin();
  if (!settled) return <Loading />;
  if (!admin) return <NotAdmin />;
  return (
    <section className="grid gap-4">
      <h1 className="text-2xl font-semibold">Admin</h1>
      <ul className="grid gap-3">
        {SCREENS.map(({ to, title, hint, icon: Icon }) => (
          <li key={to}>
            <Link
              to={to}
              className="flex items-center gap-3 rounded-xl bg-card p-4 text-card-foreground ring-1 ring-foreground/10 outline-none hover:bg-accent focus-visible:ring-3 focus-visible:ring-ring/50"
            >
              <Icon className="size-6 shrink-0 text-primary" aria-hidden="true" />
              <span className="grid flex-1 gap-0.5">
                <span className="text-base font-semibold">{title}</span>
                <span className="text-sm text-muted-foreground">{hint}</span>
              </span>
              <ChevronRightIcon className="size-5 text-muted-foreground" aria-hidden="true" />
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
