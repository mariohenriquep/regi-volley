import { PlusIcon } from 'lucide-react';
import { useId, useState } from 'react';
import { QueryState } from '@/components/QueryState';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { plural } from '@/features/coaching/model';
import { ConfirmDialog } from '../../ConfirmDialog';
import { isForbidden } from '../../forbidden';
import { useAdminNotice } from '../../useAdminNotice';
import { SetupScreen } from '../SetupScreen';
import type { Venue } from './model';
import { useDeleteVenue } from './useVenueMutations';
import { useVenues } from './useVenues';
import { VenueSheet } from './VenueSheet';

type Editing = { venue: Venue | null };

/**
 * The association's venues (US-02): name, address and courts; add, edit and delete. A venue an active group uses cannot be
 * deleted: the list says so (`inUseByActiveGroup`) and disables the button with the reason, and the API still decides when
 * asked (its refusal is shown in the question).
 */
export function VenuesPage() {
  const venues = useVenues();
  const notice = useAdminNotice();
  const [editing, setEditing] = useState<Editing | null>(null);
  const [deleting, setDeleting] = useState<Venue | null>(null);
  const remove = useDeleteVenue();

  return (
    <SetupScreen title="Venues" forbidden={isForbidden(venues.error)}>
      {notice.notice}
      <Button
        className="h-11 w-fit"
        onClick={(event) => {
          notice.remember(event.currentTarget);
          setEditing({ venue: null });
        }}
      >
        <PlusIcon aria-hidden="true" />
        Add venue
      </Button>

      <QueryState query={venues} loadingLabel="Loading venues">
        {(rows) =>
          rows.length === 0 ? (
            <div className="grid justify-items-center gap-2 py-8 text-center">
              <p className="font-medium">No venues yet</p>
              <p className="text-sm text-muted-foreground">
                Add the venue where your groups play, then create a training group there.
              </p>
            </div>
          ) : (
            <ul aria-label="Venues" className="grid gap-3">
              {rows.map((venue) => (
                <li key={venue.id}>
                  <VenueCard
                    venue={venue}
                    onEdit={(button) => {
                      notice.remember(button);
                      setEditing({ venue });
                    }}
                    onDelete={(button) => {
                      notice.remember(button);
                      remove.reset();
                      setDeleting(venue);
                    }}
                  />
                </li>
              ))}
            </ul>
          )
        }
      </QueryState>

      <Dialog
        open={editing !== null}
        onOpenChange={(open) => {
          if (!open) setEditing(null);
        }}
      >
        {editing && (
          <VenueSheet
            key={editing.venue?.id ?? 'new'}
            venue={editing.venue}
            onSaved={(name) => {
              const created = editing.venue === null;
              setEditing(null);
              notice.announce({ text: `${name} was ${created ? 'added' : 'saved'}.` });
            }}
            onClose={() => {
              setEditing(null);
            }}
            onCloseAutoFocus={notice.onCloseAutoFocus}
          />
        )}
      </Dialog>

      <ConfirmDialog
        open={deleting !== null}
        title={`Delete ${deleting?.name ?? 'the venue'}?`}
        description="The venue is removed from the association. Groups that used it in the past keep their history."
        confirmLabel="Delete venue"
        pendingLabel="Deleting…"
        destructive
        pending={remove.isPending}
        error={remove.error}
        onConfirm={() => {
          if (!deleting) return;
          remove.mutate(deleting.id, {
            onSuccess: () => {
              setDeleting(null);
              notice.announce({ text: `${deleting.name} was deleted.` });
            },
          });
        }}
        onClose={() => {
          setDeleting(null);
          remove.reset();
        }}
        onCloseAutoFocus={notice.onCloseAutoFocus}
      />
    </SetupScreen>
  );
}

function VenueCard({
  venue,
  onEdit,
  onDelete,
}: {
  venue: Venue;
  onEdit: (button: HTMLElement) => void;
  onDelete: (button: HTMLElement) => void;
}) {
  const id = useId();
  const reasonId = `${id}-in-use`;
  return (
    <article
      aria-labelledby={id}
      className="grid gap-3 rounded-xl bg-card p-4 text-card-foreground ring-1 ring-foreground/10"
    >
      <div className="grid gap-0.5">
        <h2 id={id} className="text-base font-semibold">
          {venue.name}
        </h2>
        <p className="text-sm">{venue.address}</p>
        <p className="text-sm text-muted-foreground">{plural(venue.courts, 'court', 'courts')}</p>
      </div>
      <div className="flex flex-wrap gap-2">
        <Button
          variant="outline"
          className="h-11 px-4"
          onClick={(event) => {
            onEdit(event.currentTarget);
          }}
        >
          Edit <span className="sr-only">{venue.name}</span>
        </Button>
        <Button
          variant="outline"
          className="h-11 px-4"
          disabled={venue.inUse}
          aria-describedby={venue.inUse ? reasonId : undefined}
          onClick={(event) => {
            onDelete(event.currentTarget);
          }}
        >
          Delete <span className="sr-only">{venue.name}</span>
        </Button>
      </div>
      {venue.inUse && (
        <p id={reasonId} className="text-sm text-muted-foreground">
          An active training group uses this venue. Archive the group to be able to delete it.
        </p>
      )}
    </article>
  );
}
