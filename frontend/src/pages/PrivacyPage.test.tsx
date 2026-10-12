import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { POLICY_VERSION } from '@/lib/privacy';
import { PrivacyPage } from './PrivacyPage';

/**
 * The text of each policy version, as a hash of what the page shows (the version number itself left out). A consent
 * records the version the person agreed to, so the text of a version must never change: edit the policy, bump
 * `POLICY_VERSION` in `src/lib/privacy.ts` and add the new hash here. The failing test prints the hash to add.
 */
const PINNED_TEXT_HASH: Record<string, string> = {
  '2026-01': '21950a315856e85d090f28daa85db7d4b608398601ae174a241810d41019ca32',
};

async function policyTextHash(): Promise<string> {
  const { container } = render(<PrivacyPage />);
  const text = container.textContent
    .replaceAll(`Version ${POLICY_VERSION}.`, 'Version <current>.')
    .replace(/\s+/g, ' ')
    .trim();
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, '0')).join('');
}

describe('privacy policy text', () => {
  it('has a pinned hash for the current policy version', () => {
    // Arrange / Act
    const pinned = PINNED_TEXT_HASH[POLICY_VERSION];

    // Assert
    expect(pinned, `Pin the text of policy version ${POLICY_VERSION} in this test`).toBeDefined();
  });

  it('matches the text pinned for its version, so a changed text needs a new version', async () => {
    // Arrange / Act
    const hash = await policyTextHash();

    // Assert
    expect(
      hash,
      `The privacy text changed without a new POLICY_VERSION (current ${POLICY_VERSION}). ` +
        `Bump it in src/lib/privacy.ts and pin the new hash: ${hash}`,
    ).toBe(PINNED_TEXT_HASH[POLICY_VERSION]);
  });
});
