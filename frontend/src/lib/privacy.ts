/**
 * The version of the privacy policy a person accepts on the join and registration forms. The API stores it with the
 * consent (`policyVersion`, at most 50 characters) as the record of what was agreed to; it does not validate the value.
 *
 * Bump it whenever `src/pages/PrivacyPage.tsx` changes, so each consent names the text it was given against:
 * `PrivacyPage.test.tsx` pins a hash of the text per version and fails when the text changes without a new version.
 * The text itself is a stub until the association's legal team supplies the final wording (docs/architecture.md,
 * section 15).
 */
export const POLICY_VERSION = '2026-01';
