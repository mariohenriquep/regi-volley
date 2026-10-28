import { describe, expect, it } from 'vitest';
import { toVenue, toVenues } from './model';

describe('toVenues', () => {
  it('reads each row once, with whether an active group uses it', () => {
    // Arrange
    const raw = [
      { id: 'a', name: 'North', address: 'Street 1', courts: 2, inUseByActiveGroup: true },
      { id: 'b', name: 'South', address: 'Street 2', courts: 1 },
    ];

    // Act
    const venues = toVenues(raw);

    // Assert
    expect(venues).toEqual([
      { id: 'a', name: 'North', address: 'Street 1', courts: 2, inUse: true },
      { id: 'b', name: 'South', address: 'Street 2', courts: 1, inUse: false },
    ]);
  });

  it('drops a row without an id or a court count, and treats a payload that is not a list as empty', () => {
    expect(toVenue({ name: 'No id', courts: 1 })).toBeNull();
    expect(toVenue({ id: 'a', name: 'No courts' })).toBeNull();
    expect(toVenues({ not: 'a list' })).toEqual([]);
    expect(toVenues([null, 3, { id: 'a', courts: 1 }])).toHaveLength(1);
  });
});
