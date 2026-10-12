import { Link } from 'react-router';
import { buttonVariants } from '@/components/ui/button';
import { usePageTitle } from '@/lib/usePageTitle';

/** The friendly answer to a short name that names no association (the API says 404; the page is public by design). */
export function AssociationNotFound() {
  usePageTitle('Association not found');
  return (
    <section className="grid gap-4">
      <h1 className="text-2xl font-semibold">Association not found</h1>
      <p>
        We could not find an association at this address. Check the address, or ask the association
        for its link.
      </p>
      <Link to="/register" className={buttonVariants({ variant: 'outline' })}>
        Register an association
      </Link>
    </section>
  );
}
