import { useState } from 'react';
import { TextField } from '@/components/form/TextField';
import { FormSheet } from '../FormSheet';
import { wholeNumber, wholeNumberHint } from '../numbers';
import { useFormProblems } from '../useFormProblems';
import { MAX_COURTS, MAX_VENUE_ADDRESS, MAX_VENUE_NAME, MIN_COURTS, type Venue } from './model';
import { useSaveVenue } from './useVenueMutations';

const LABELS = { 'venue-name': 'Name', 'venue-address': 'Address', 'venue-courts': 'Courts' };
// A 400 names the request property, a 422 the rule's label ("venue name"): both meet in one normalised key.
const FIELD_MAP = {
  name: 'venue-name',
  venuename: 'venue-name',
  address: 'venue-address',
  venueaddress: 'venue-address',
  courts: 'venue-courts',
};

/** Creates a venue or edits one (US-02): name, address and number of courts. Every other rule is the API's. */
export function VenueSheet({
  venue,
  onSaved,
  onClose,
  onCloseAutoFocus,
}: {
  /** `null` to add a venue. */
  venue: Venue | null;
  onSaved: (name: string) => void;
  onClose: () => void;
  onCloseAutoFocus: (event: Event) => void;
}) {
  const save = useSaveVenue();
  const [name, setName] = useState(venue?.name ?? '');
  const [address, setAddress] = useState(venue?.address ?? '');
  const [courts, setCourts] = useState(venue ? String(venue.courts) : '');
  const { problems, fail, clear } = useFormProblems(save.error, FIELD_MAP);

  const submit = () => {
    const missing: Record<string, string> = {};
    if (!name.trim()) missing['venue-name'] = 'Enter the venue name';
    if (!address.trim()) missing['venue-address'] = 'Enter the address';
    const count = wholeNumber(courts, MIN_COURTS, MAX_COURTS);
    if (count === null) {
      missing['venue-courts'] = courts.trim()
        ? wholeNumberHint(MIN_COURTS, MAX_COURTS)
        : 'Enter the number of courts';
    }
    if (Object.keys(missing).length > 0 || count === null) {
      fail(missing);
      return;
    }
    clear();
    save.mutate(
      { id: venue?.id ?? null, name: name.trim(), address: address.trim(), courts: count },
      {
        onSuccess: () => {
          onSaved(name.trim());
        },
      },
    );
  };

  return (
    <FormSheet
      title={venue ? `Edit ${venue.name}` : 'Add a venue'}
      description="Groups play at a venue. Courts is how many courts it has."
      submitLabel={venue ? 'Save venue' : 'Add venue'}
      pendingLabel="Saving…"
      pending={save.isPending}
      problems={problems}
      labels={LABELS}
      onSubmit={submit}
      onClose={onClose}
      onCloseAutoFocus={onCloseAutoFocus}
    >
      <TextField
        id="venue-name"
        label="Name"
        maxLength={MAX_VENUE_NAME}
        required
        value={name}
        error={problems?.fields['venue-name']}
        onChange={(event) => {
          setName(event.target.value);
        }}
      />
      <TextField
        id="venue-address"
        label="Address"
        autoComplete="off"
        maxLength={MAX_VENUE_ADDRESS}
        required
        value={address}
        error={problems?.fields['venue-address']}
        onChange={(event) => {
          setAddress(event.target.value);
        }}
      />
      <TextField
        id="venue-courts"
        label="Courts"
        type="number"
        inputMode="numeric"
        min={MIN_COURTS}
        max={MAX_COURTS}
        required
        value={courts}
        error={problems?.fields['venue-courts']}
        onChange={(event) => {
          setCourts(event.target.value);
        }}
      />
    </FormSheet>
  );
}
