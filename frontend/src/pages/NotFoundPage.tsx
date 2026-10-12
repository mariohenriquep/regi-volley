import { Link } from 'react-router';

export function NotFoundPage() {
  return (
    <section className="grid gap-4">
      <h1 className="text-2xl font-semibold">Page not found</h1>
      <p>This page does not exist.</p>
      <Link to="/" className="text-primary underline underline-offset-4">
        Go to the home page
      </Link>
    </section>
  );
}
