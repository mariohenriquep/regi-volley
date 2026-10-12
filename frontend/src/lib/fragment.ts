/**
 * Reads the `token` parameter of the URL fragment (`/activate#token=...`, `/reset-password#token=...`) and immediately
 * removes the whole fragment from the address bar with `history.replaceState` (threat model D-11): the token then
 * survives in no history entry, bookmark, screenshot of the address bar or shared URL. The path, query string and the
 * router's history state are kept. Returns null when there is no usable token.
 *
 * Call it once per visit (the route loader does): after it ran, the fragment is gone.
 */
export function takeFragmentToken(
  win: Pick<Window, 'location' | 'history'> = window,
): string | null {
  const { hash, pathname, search } = win.location;
  if (!hash) return null;
  win.history.replaceState(win.history.state, '', pathname + search);
  return tokenFromHash(hash);
}

/** The `token` parameter of a fragment (`#token=...`), decoded; null when there is none or it is not decodable. */
export function tokenFromHash(hash: string): string | null {
  const raw = hash
    .slice(1)
    .split('&')
    .find((part) => part.startsWith('token='))
    ?.slice('token='.length);
  if (!raw) return null;
  try {
    return decodeURIComponent(raw);
  } catch {
    return null;
  }
}
