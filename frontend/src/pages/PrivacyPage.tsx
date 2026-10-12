import { POLICY_VERSION } from '@/lib/privacy';
import { usePageTitle } from '@/lib/usePageTitle';

/**
 * Stub of the privacy policy the consent checkboxes link to. The wording is a placeholder that says what the app does
 * today; the final text comes from the association's legal review, and then `POLICY_VERSION` changes.
 */
export function PrivacyPage() {
  usePageTitle('Privacy policy');
  return (
    <article className="grid gap-4">
      <h1 className="text-2xl font-semibold">Privacy policy</h1>
      <p className="text-sm text-muted-foreground">
        Version {POLICY_VERSION}. This is a placeholder: the final text is pending legal review.
      </p>
      <section className="grid gap-2" aria-labelledby="privacy-data">
        <h2 id="privacy-data" className="text-lg font-semibold">
          What we collect
        </h2>
        <p>
          Your name, email address and phone number, and the record of your bookings, attendance and
          payments with the association you join.
        </p>
      </section>
      <section className="grid gap-2" aria-labelledby="privacy-use">
        <h2 id="privacy-use" className="text-lg font-semibold">
          What it is used for
        </h2>
        <p>
          To run the association&apos;s training sessions: to review your request to join, to book
          you into sessions and to email you about your bookings. It is shown only to that
          association&apos;s administrators and coaches.
        </p>
      </section>
      <section className="grid gap-2" aria-labelledby="privacy-keep">
        <h2 id="privacy-keep" className="text-lg font-semibold">
          How long we keep it
        </h2>
        <p>
          A request to join that nobody answers is anonymised after 30 days. You can ask the
          association to export or erase your data at any time.
        </p>
      </section>
    </article>
  );
}
