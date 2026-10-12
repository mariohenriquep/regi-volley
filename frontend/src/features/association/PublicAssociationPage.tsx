import { Link, useParams } from 'react-router';
import { ApiError } from '@/api/errors';
import { ErrorAlert } from '@/components/ErrorAlert';
import { Loading } from '@/components/Loading';
import { buttonVariants } from '@/components/ui/button';
import { usePageTitle } from '@/lib/usePageTitle';
import { AssociationNotFound } from './AssociationNotFound';
import { slotText } from './schedule';
import { usePublicAssociation } from './usePublicAssociation';

/** Only a plain address becomes a mailto: link; anything with a query or header separator is shown as text. */
const PLAIN_EMAIL = /^[^\s@?&#%,;<>]+@[^\s@?&#%,;<>]+$/;

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  const id = `section-${title.toLowerCase().replace(/\s+/g, '-')}`;
  return (
    <section aria-labelledby={id} className="grid gap-2">
      <h2 id={id} className="text-lg font-semibold">
        {title}
      </h2>
      {children}
    </section>
  );
}

/** The association's public page (US-24): everything a visitor needs to decide to try a session. */
export function PublicAssociationPage() {
  const { shortName = '' } = useParams();
  const association = usePublicAssociation(shortName);
  usePageTitle(association.data?.name ?? 'Association');

  if (association.isPending) return <Loading />;
  if (association.error instanceof ApiError && association.error.status === 404) {
    return <AssociationNotFound />;
  }
  if (association.error) return <ErrorAlert error={association.error} />;

  const { name, locality, contactEmail, levels = [], groups = [], venues = [] } = association.data;
  return (
    <article className="grid gap-6">
      <header className="grid gap-1">
        <h1 className="text-2xl font-semibold">{name}</h1>
        <p className="text-muted-foreground">{locality}</p>
        {contactEmail &&
          (PLAIN_EMAIL.test(contactEmail) ? (
            <a
              href={`mailto:${contactEmail}`}
              className="w-fit text-primary underline underline-offset-4"
            >
              {contactEmail}
            </a>
          ) : (
            <p>{contactEmail}</p>
          ))}
      </header>

      <Link
        to={`/a/${encodeURIComponent(shortName)}/join`}
        className={buttonVariants({ size: 'lg', className: 'h-11' })}
      >
        Want to try it?
      </Link>

      <Section title="Levels">
        <p className="text-sm text-muted-foreground">Lowest first.</p>
        <ul className="list-inside list-disc">
          {levels.map((level) => (
            <li key={level}>{level}</li>
          ))}
        </ul>
      </Section>

      <Section title="Training groups">
        {groups.length === 0 && <p>No training groups yet.</p>}
        {groups.map((group) => (
          <div key={group.name} className="grid gap-1 rounded-lg border p-3">
            <h3 className="font-medium">{group.name}</h3>
            <ul>
              {(group.schedule ?? []).map((slot) => (
                <li key={`${slot.dayOfWeek ?? ''}${slot.startTime ?? ''}`}>{slotText(slot)}</li>
              ))}
            </ul>
            <p className="text-sm text-muted-foreground">
              {group.venue && <>Venue: {group.venue}. </>}
              {(group.levels ?? []).length > 0 && <>Levels: {(group.levels ?? []).join(', ')}.</>}
            </p>
          </div>
        ))}
        {groups.length > 0 && (
          <p className="text-sm text-muted-foreground">Times are Lisbon time.</p>
        )}
      </Section>

      <Section title="Venues">
        {venues.length === 0 && <p>No venues yet.</p>}
        {venues.map((venue) => (
          <div key={`${venue.name ?? ''}${venue.address ?? ''}`} className="grid gap-1">
            <h3 className="font-medium">{venue.name}</h3>
            <p>{venue.address}</p>
            {venue.courts !== undefined && (
              <p className="text-sm text-muted-foreground">
                {String(venue.courts)} {venue.courts === 1 ? 'court' : 'courts'}
              </p>
            )}
          </div>
        ))}
      </Section>
    </article>
  );
}
