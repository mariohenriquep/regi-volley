import { useMutation } from '@tanstack/react-query';
import { ArrowDown, ArrowUp, Plus, Trash2 } from 'lucide-react';
import { type SubmitEvent, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { ApiError, unwrap } from '@/api/errors';
import { useAuth } from '@/auth/context';
import { Confirmation } from '@/components/Confirmation';
import { ErrorSummary } from '@/components/form/ErrorSummary';
import { PolicyConsent } from '@/components/form/PolicyConsent';
import {
  type FieldMap,
  type FormProblems,
  fromApiError,
  fromClient,
} from '@/components/form/problems';
import { TextField } from '@/components/form/TextField';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { EMAIL_SHAPE } from '@/lib/email';
import { POLICY_VERSION } from '@/lib/privacy';
import { usePageTitle } from '@/lib/usePageTitle';

/** Limits of the API's request (docs/api/openapi.json): the inputs stop there, the API stays the authority. */
const MAX = {
  name: 100,
  shortName: 40,
  nif: 20,
  locality: 100,
  email: 254,
  phone: 30,
  levelName: 50,
  levels: 20,
} as const;

const LABELS: Record<string, string> = {
  name: 'Association name',
  shortName: 'Short name',
  nif: 'NIF',
  locality: 'Locality',
  contactEmail: 'Contact email',
  levels: 'Levels',
  founderName: 'Your name',
  founderEmail: 'Your email',
  founderPhone: 'Your phone',
  consent: 'Consent',
};

/** The API names a field by the request property (400) or by the rule's label (422): both meet here. */
const FIELD_MAP: FieldMap = {
  name: 'founderName',
  associationname: 'name',
  shortname: 'shortName',
  nif: 'nif',
  locality: 'locality',
  contactemail: 'contactEmail',
  levelnames: 'levels',
  levelname: 'levels',
  foundername: 'founderName',
  founderemail: 'founderEmail',
  founderphone: 'founderPhone',
  phone: 'founderPhone',
  consentaccepted: 'consent',
};

interface Level {
  key: number;
  name: string;
}

/** The address the public page will have, as a visitor will see it. Display only: the API normalises and decides. */
const previewOf = (shortName: string) => shortName.trim().toLowerCase();

/** Register an association (US-01): the founder becomes its first administrator once they activate their account. */
export function RegisterPage() {
  const { publicApi } = useAuth();
  usePageTitle('Register an association');

  const [values, setValues] = useState({
    name: '',
    shortName: '',
    nif: '',
    locality: '',
    contactEmail: '',
    founderName: '',
    founderEmail: '',
    founderPhone: '',
  });
  const [levels, setLevels] = useState<Level[]>([{ key: 0, name: '' }]);
  const nextKey = useRef(1);
  const [focusKey, setFocusKey] = useState<number | null>(null);
  const [consent, setConsent] = useState(false);
  const [problems, setProblems] = useState<FormProblems | null>(null);

  const register = useMutation({
    mutationFn: () =>
      unwrap(
        publicApi.POST('/api/v1/public/associations', {
          body: {
            ...values,
            nif: values.nif.trim() || undefined,
            levelNames: levels.map((level) => level.name),
            consentAccepted: consent,
            policyVersion: POLICY_VERSION,
          },
        }),
      ),
    onError: (error) => {
      // The only conflict registering can raise is a short name that is taken.
      if (error instanceof ApiError && error.status === 409) {
        setProblems({
          fields: { shortName: error.message },
          general: [],
          requestId: error.requestId,
        });
      } else {
        setProblems(fromApiError(error, FIELD_MAP));
      }
    },
  });

  useEffect(() => {
    if (focusKey !== null) document.getElementById(`level-${String(focusKey)}`)?.focus();
  }, [focusKey]);

  if (register.isSuccess) {
    const publicPage = register.data.shortName;
    return (
      <Confirmation title="Check your email to activate your account">
        <p>
          We sent an activation link to the founder&apos;s email address. Open it to choose a
          password and sign in.
        </p>
        {publicPage && (
          <p>
            Meanwhile,{' '}
            <Link
              to={`/a/${encodeURIComponent(publicPage)}`}
              className="text-primary underline underline-offset-4"
            >
              your public page
            </Link>{' '}
            is already online.
          </p>
        )}
        <Link to="/login" className="w-fit text-primary underline underline-offset-4">
          Sign in
        </Link>
      </Confirmation>
    );
  }

  const set = (field: keyof typeof values) => (event: { target: { value: string } }) => {
    setValues((current) => ({ ...current, [field]: event.target.value }));
  };

  const moveLevel = (index: number, by: -1 | 1) => {
    setLevels((current) => {
      const next = [...current];
      const [moved] = next.splice(index, 1);
      if (moved) next.splice(index + by, 0, moved);
      return next;
    });
  };

  const addLevel = () => {
    const key = nextKey.current;
    nextKey.current += 1;
    setLevels((current) => [...current, { key, name: '' }]);
    setFocusKey(key);
  };

  const onSubmit = (event: SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    const missing: Record<string, string> = {};
    if (!values.name.trim()) missing.name = 'Enter the association name';
    if (!values.shortName.trim()) missing.shortName = 'Enter a short name';
    if (!values.locality.trim()) missing.locality = 'Enter the locality';
    if (!values.contactEmail.trim()) missing.contactEmail = 'Enter a contact email address';
    else if (!EMAIL_SHAPE.test(values.contactEmail.trim()))
      missing.contactEmail = 'Enter a valid email address';
    if (levels.some((level) => !level.name.trim()))
      missing.levels = 'Give every level a name, or remove the empty ones';
    if (!values.founderName.trim()) missing.founderName = 'Enter your name';
    if (!values.founderEmail.trim()) missing.founderEmail = 'Enter your email address';
    else if (!EMAIL_SHAPE.test(values.founderEmail.trim()))
      missing.founderEmail = 'Enter a valid email address';
    if (!values.founderPhone.trim()) missing.founderPhone = 'Enter your phone number';
    if (!consent) missing.consent = 'Accept the privacy policy to register';
    if (Object.keys(missing).length > 0) {
      setProblems(fromClient(missing));
      return;
    }
    setProblems(null);
    register.mutate();
  };

  const preview = previewOf(values.shortName);

  return (
    <section className="grid gap-4">
      <div className="grid gap-1">
        <h1 className="text-2xl font-semibold">Register an association</h1>
        <p>
          You become its first administrator once you activate your account from the email we send
          you.
        </p>
      </div>
      <form className="grid gap-4" method="post" onSubmit={onSubmit} noValidate>
        <ErrorSummary problems={problems} labels={LABELS} />

        <TextField
          id="name"
          label="Association name"
          autoComplete="organization"
          maxLength={MAX.name}
          required
          value={values.name}
          onChange={set('name')}
          error={problems?.fields.name}
        />
        <TextField
          id="shortName"
          label="Short name"
          autoCapitalize="none"
          autoCorrect="off"
          spellCheck={false}
          maxLength={MAX.shortName}
          required
          value={values.shortName}
          onChange={set('shortName')}
          hint={
            preview
              ? `${window.location.origin}/a/${preview}`
              : 'Lowercase letters, digits and hyphens. It becomes the address of your public page.'
          }
          error={problems?.fields.shortName}
        />
        <TextField
          id="nif"
          label="NIF (optional)"
          inputMode="numeric"
          maxLength={MAX.nif}
          value={values.nif}
          onChange={set('nif')}
          error={problems?.fields.nif}
        />
        <TextField
          id="locality"
          label="Locality"
          autoComplete="address-level2"
          maxLength={MAX.locality}
          required
          value={values.locality}
          onChange={set('locality')}
          error={problems?.fields.locality}
        />
        <TextField
          id="contactEmail"
          label="Contact email"
          type="email"
          maxLength={MAX.email}
          required
          value={values.contactEmail}
          onChange={set('contactEmail')}
          hint="This address is shown publicly on your association's page."
          error={problems?.fields.contactEmail}
        />

        <fieldset
          id="levels"
          tabIndex={-1}
          aria-describedby="levels-hint"
          className="grid gap-3 outline-none"
        >
          <legend className="mb-1 text-sm font-medium">Levels</legend>
          <p id="levels-hint" className="text-sm text-muted-foreground">
            List the levels from the lowest to the highest. The first level is the entry level: new
            members start there.
          </p>
          {levels.map((level, index) => (
            <div key={level.key} className="grid gap-2">
              <Label htmlFor={`level-${String(level.key)}`}>{`Level ${String(index + 1)}`}</Label>
              <div className="flex items-center gap-1">
                <Input
                  id={`level-${String(level.key)}`}
                  className="h-10"
                  maxLength={MAX.levelName}
                  value={level.name}
                  onChange={(event) => {
                    setLevels((current) =>
                      current.map((item) =>
                        item.key === level.key ? { ...item, name: event.target.value } : item,
                      ),
                    );
                  }}
                />
                <Button
                  type="button"
                  variant="outline"
                  size="icon"
                  className="size-10"
                  aria-label={`Move level ${String(index + 1)} up`}
                  disabled={index === 0}
                  onClick={() => {
                    moveLevel(index, -1);
                  }}
                >
                  <ArrowUp aria-hidden="true" />
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="icon"
                  className="size-10"
                  aria-label={`Move level ${String(index + 1)} down`}
                  disabled={index === levels.length - 1}
                  onClick={() => {
                    moveLevel(index, 1);
                  }}
                >
                  <ArrowDown aria-hidden="true" />
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="icon"
                  className="size-10"
                  aria-label={`Remove level ${String(index + 1)}`}
                  disabled={levels.length === 1}
                  onClick={() => {
                    setLevels((current) => current.filter((item) => item.key !== level.key));
                  }}
                >
                  <Trash2 aria-hidden="true" />
                </Button>
              </div>
            </div>
          ))}
          {problems?.fields.levels && (
            <p className="text-sm text-destructive">{problems.fields.levels}</p>
          )}
          <Button
            type="button"
            variant="outline"
            disabled={levels.length >= MAX.levels}
            onClick={addLevel}
          >
            <Plus aria-hidden="true" /> Add level
          </Button>
        </fieldset>

        <h2 className="mt-2 text-lg font-semibold">About you</h2>
        <TextField
          id="founderName"
          label="Your name"
          autoComplete="name"
          maxLength={MAX.name}
          required
          value={values.founderName}
          onChange={set('founderName')}
          error={problems?.fields.founderName}
        />
        <TextField
          id="founderEmail"
          label="Your email"
          type="email"
          autoComplete="email"
          maxLength={MAX.email}
          required
          value={values.founderEmail}
          onChange={set('founderEmail')}
          hint="We send the activation link here."
          error={problems?.fields.founderEmail}
        />
        <TextField
          id="founderPhone"
          label="Your phone"
          type="tel"
          autoComplete="tel"
          maxLength={MAX.phone}
          required
          value={values.founderPhone}
          onChange={set('founderPhone')}
          error={problems?.fields.founderPhone}
        />

        <PolicyConsent
          id="consent"
          checked={consent}
          onChange={setConsent}
          error={problems?.fields.consent}
          purpose="to run the association and contact me about it"
        />
        <Button type="submit" size="lg" className="h-11" disabled={register.isPending}>
          {register.isPending ? 'Registering…' : 'Register'}
        </Button>
        <p className="text-sm">
          Already registered?{' '}
          <Link to="/login" className="text-primary underline underline-offset-4">
            Sign in
          </Link>
        </p>
      </form>
    </section>
  );
}
