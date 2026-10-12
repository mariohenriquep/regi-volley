import type { ShouldRevalidateFunction } from 'react-router';
import { takeFragmentToken } from '@/lib/fragment';

/**
 * A token that can be taken exactly once. The router keeps loader data for as long as the route is shown, so the token
 * is not left in it: the form that needs it takes it into its own state, and the holder is empty from then on.
 */
export interface TokenHolder {
  /** The token the first time, `null` ever after. */
  take: () => string | null;
}

export function createTokenHolder(token: string | null): TokenHolder {
  let held = token;
  return {
    take: () => {
      const taken = held;
      held = null;
      return taken;
    },
  };
}

export interface AccountLinkData {
  /** Holds the single-use token of the emailed link until the form takes it; never rendered. */
  link: TokenHolder;
}

/**
 * Loader of /activate and /reset-password: reads the token from the fragment and scrubs it from the address bar
 * before the page renders (threat model D-11), handing it over in a one-shot {@link TokenHolder}. Runs once per visit:
 * these routes never revalidate.
 */
export const accountLinkLoader = (): AccountLinkData => ({
  link: createTokenHolder(takeFragmentToken()),
});

export const neverRevalidate: ShouldRevalidateFunction = () => false;
