import { PlusIcon } from 'lucide-react';
import { useId, useState } from 'react';
import { useSearchParams } from 'react-router';
import { QueryState } from '@/components/QueryState';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog } from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { plural } from '@/features/coaching/model';
import { ConfirmDialog } from '../../ConfirmDialog';
import { isForbidden } from '../../forbidden';
import { useAdminNotice } from '../../useAdminNotice';
import { SetupScreen } from '../SetupScreen';
import { GroupSheet } from './GroupSheet';
import { type Group, isArchived, slotText } from './model';
import { useArchiveGroup } from './useGroupMutations';
import { useGroups } from './useGroups';

type Editing = { group: Group | null };

/** Sessions are made by the daily generation (RN-01), which has no HTTP route: saving a group does not make any. */
export const GENERATION_NOTE = 'Sessions for this group appear after the next daily generation.';

/**
 * The association's training groups (US-09, RN-21): name, accepted levels, venue, weekly schedule in Lisbon time, seats, coach
 * and status. Archived groups are hidden unless asked for (`?archived=1`, so a reload keeps the choice). Archiving stops future
 * sessions and leaves the existing ones as they are; it asks first.
 */
export function GroupsPage() {
  const [params, setParams] = useSearchParams();
  const showArchived = params.get('archived') === '1';
  const groups = useGroups(showArchived);
  const notice = useAdminNotice();
  const [editing, setEditing] = useState<Editing | null>(null);
  const [archiving, setArchiving] = useState<Group | null>(null);
  const archive = useArchiveGroup();

  return (
    <SetupScreen title="Training groups" forbidden={isForbidden(groups.error)}>
      {notice.notice}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Button
          className="h-11"
          onClick={(event) => {
            notice.remember(event.currentTarget);
            setEditing({ group: null });
          }}
        >
          <PlusIcon aria-hidden="true" />
          Add group
        </Button>
        <div className="flex items-center gap-3">
          <Checkbox
            id="show-archived"
            className="size-5"
            checked={showArchived}
            onCheckedChange={(checked) => {
              setParams(checked === true ? { archived: '1' } : {}, { replace: true });
            }}
          />
          <Label htmlFor="show-archived" className="text-base font-normal md:text-sm">
            Show archived groups
          </Label>
        </div>
      </div>

      <QueryState query={groups} loadingLabel="Loading training groups">
        {(rows) =>
          rows.length === 0 ? (
            <div className="grid justify-items-center gap-2 py-8 text-center">
              <p className="font-medium">No training groups yet</p>
              <p className="text-sm text-muted-foreground">
                A group needs a venue and a coach. Add a group and its weekly schedule; sessions are
                created from it every day.
              </p>
            </div>
          ) : (
            <ul aria-label="Training groups" className="grid gap-3">
              {rows.map((group) => (
                <li key={group.id}>
                  <GroupCard
                    group={group}
                    onEdit={(button) => {
                      notice.remember(button);
                      setEditing({ group });
                    }}
                    onArchive={(button) => {
                      notice.remember(button);
                      archive.reset();
                      setArchiving(group);
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
          <GroupSheet
            key={editing.group?.id ?? 'new'}
            group={editing.group}
            onSaved={(name) => {
              const created = editing.group === null;
              setEditing(null);
              notice.announce({
                text: `${name} was ${created ? 'added' : 'saved'}. ${GENERATION_NOTE}`,
              });
            }}
            onClose={() => {
              setEditing(null);
            }}
            onCloseAutoFocus={notice.onCloseAutoFocus}
          />
        )}
      </Dialog>

      <ConfirmDialog
        open={archiving !== null}
        title={`Archive ${archiving?.name ?? 'the group'}?`}
        description="The group stops creating new sessions. Sessions that already exist are not changed or cancelled, and members keep their bookings. Archiving cannot be undone in the app, and an archived group cannot be edited."
        confirmLabel="Archive group"
        pendingLabel="Archiving…"
        destructive
        pending={archive.isPending}
        error={archive.error}
        onConfirm={() => {
          if (!archiving) return;
          archive.mutate(archiving.id, {
            onSuccess: () => {
              setArchiving(null);
              notice.announce({
                text: `${archiving.name} was archived. Its existing sessions are unchanged.`,
              });
            },
          });
        }}
        onClose={() => {
          setArchiving(null);
          archive.reset();
        }}
        onCloseAutoFocus={notice.onCloseAutoFocus}
      />
    </SetupScreen>
  );
}

function GroupCard({
  group,
  onEdit,
  onArchive,
}: {
  group: Group;
  onEdit: (button: HTMLElement) => void;
  onArchive: (button: HTMLElement) => void;
}) {
  const id = useId();
  const archived = isArchived(group);
  return (
    <article
      aria-labelledby={id}
      className="grid gap-3 rounded-xl bg-card p-4 text-card-foreground ring-1 ring-foreground/10"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id={id} className="text-base font-semibold">
          {group.name}
        </h2>
        {group.status === 'ACTIVE' ? (
          <Badge variant="success">Active</Badge>
        ) : (
          <Badge variant="secondary">{archived ? 'Archived' : group.status}</Badge>
        )}
      </div>
      <dl className="grid gap-1 text-sm">
        <Row term="Levels" value={group.acceptedLevels.map(({ name }) => name).join(', ')} />
        <Row term="Venue" value={group.venue?.name} />
        <Row term="Coach" value={group.coach?.name} />
        <Row term="Seats" value={plural(group.capacity, 'seat', 'seats')} />
      </dl>
      <div className="grid gap-1">
        <h3 className="text-sm font-medium">Weekly schedule (Lisbon time)</h3>
        <ul aria-label="Weekly schedule" className="grid gap-0.5 text-sm">
          {group.schedule.map((slot) => (
            <li key={`${slot.dayOfWeek}-${slot.startTime}`}>{slotText(slot)}</li>
          ))}
        </ul>
      </div>
      {!archived && (
        <div className="flex flex-wrap gap-2">
          <Button
            variant="outline"
            className="h-11 px-4"
            onClick={(event) => {
              onEdit(event.currentTarget);
            }}
          >
            Edit <span className="sr-only">{group.name}</span>
          </Button>
          <Button
            variant="outline"
            className="h-11 px-4"
            onClick={(event) => {
              onArchive(event.currentTarget);
            }}
          >
            Archive <span className="sr-only">{group.name}</span>
          </Button>
        </div>
      )}
    </article>
  );
}

function Row({ term, value }: { term: string; value: string | undefined }) {
  if (!value) return null;
  return (
    <div className="flex gap-2">
      <dt className="w-16 shrink-0 text-muted-foreground">{term}</dt>
      <dd>{value}</dd>
    </div>
  );
}
