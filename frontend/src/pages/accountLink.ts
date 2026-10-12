import type { ShouldRevalidateFunction } from 'react-router';
import { takeFragmentToken } from '@/lib/fragment';

export interface AccountLinkData {
  /** The single-use token of the emailed link; kept in memory only, never rendered. */
  token: string | null;
}

/**
 * Loader of /activate and /reset-password: reads the token from the fragment and scrubs it from the address bar
 * before the page renders (threat model D-11). Runs once per visit: these routes never revalidate.
 */
export const accountLinkLoader = (): AccountLinkData => ({ token: takeFragmentToken() });

export const neverRevalidate: ShouldRevalidateFunction = () => false;
