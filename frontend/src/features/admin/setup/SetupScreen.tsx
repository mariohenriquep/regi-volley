import { ArrowLeftIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { usePageTitle } from '@/lib/usePageTitle';
import { NotAdmin } from '../NotAdmin';

/** The frame of a setup screen: its title, the way back to the Admin home, and what the API said when the caller is no administrator. */
export function SetupScreen({
  title,
  forbidden,
  children,
}: {
  title: string;
  /** The API answered 403: the screen then says who it is for and shows nothing else. */
  forbidden: boolean;
  children: ReactNode;
}) {
  usePageTitle(title);
  return (
    <section className="grid gap-4">
      <h1 className="text-2xl font-semibold">{title}</h1>
      {forbidden ? (
        <NotAdmin />
      ) : (
        <>
          <Link
            to="/admin"
            className="flex w-fit items-center gap-1 text-sm text-primary underline underline-offset-4"
          >
            <ArrowLeftIcon className="size-4" aria-hidden="true" />
            Back to Admin
          </Link>
          {children}
        </>
      )}
    </section>
  );
}
