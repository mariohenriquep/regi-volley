import { describe, expect, it } from 'vitest';
import { backToList } from './listState';

describe('backToList', () => {
  it('returns to the list with the search it came from', () => {
    expect(backToList({ search: '?q=ana&status=ACTIVE' })).toBe(
      '/admin/members?q=ana&status=ACTIVE',
    );
  });

  it.each([
    ['no state', null],
    ['a state of another shape', { from: '/somewhere' }],
    ['a search that is not a query string', { search: '//evil.example/' }],
    ['a search that is not text', { search: 42 }],
  ])('goes to the plain list for %s', (_name, state) => {
    expect(backToList(state)).toBe('/admin/members');
  });
});
