/**
 * What the list tells the member page it came from, so "Back to Members" returns to the same search and filter. Navigation
 * state is not trusted: only a query string (`?...`) is taken from it, and it is appended to a fixed path.
 */
export interface ListState {
  search: string;
}

export function backToList(state: unknown): string {
  const search = (state as Partial<ListState> | null)?.search;
  return typeof search === 'string' && search.startsWith('?')
    ? `/admin/members${search}`
    : '/admin/members';
}
