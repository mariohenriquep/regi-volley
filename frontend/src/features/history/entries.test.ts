import { describe, expect, it } from 'vitest';
import { historyEntry } from '@/test/fixtures';
import { toHistory } from './entries';

const base = { noShowsThisMonth: 0, noShowLimit: 3, nearLimit: false };

describe('toHistory', () => {
  it('reads the entries as the API sends them', () => {
    // Arrange / Act
    const history = toHistory({
      ...base,
      entries: [
        historyEntry({ trainingGroupName: 'Tuesday evening', venueName: 'Pavilhão Central' }),
      ],
    });

    // Assert
    expect(history.entries).toEqual([
      expect.objectContaining({
        status: 'ATTENDED',
        startsAt: '2026-07-07T19:30:00Z',
        trainingGroupName: 'Tuesday evening',
        venueName: 'Pavilhão Central',
      }),
    ]);
  });

  it('drops entries with unreadable dates, and tolerates entries that are not a list', () => {
    // Arrange / Act
    const history = toHistory({
      ...base,
      entries: [
        historyEntry(),
        historyEntry({ startsAt: 'last week' }),
        historyEntry({ endsAt: '' }),
      ],
    });

    // Assert
    expect(history.entries).toHaveLength(1);
    expect(toHistory({ ...base, entries: 'oops' as never }).entries).toEqual([]);
  });
});
