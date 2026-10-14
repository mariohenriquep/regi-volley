import { describe, expect, it } from 'vitest';
import { joinRequest } from '@/test/fixtures';
import { toJoinRequests } from './model';

describe('toJoinRequests', () => {
  it('keeps the order the API gave', () => {
    // Arrange
    const raw = [
      joinRequest({ id: 'b0000000-0000-0000-0000-000000000001', name: 'First' }),
      joinRequest({ id: 'b0000000-0000-0000-0000-000000000002', name: 'Second' }),
    ];

    // Act / Assert
    expect(toJoinRequests(raw).map((request) => request.name)).toEqual(['First', 'Second']);
  });

  it('skips a row without an id or with a request date that cannot be read', () => {
    // Arrange
    const raw = [
      { name: 'No id', requestedAt: '2026-07-13T09:15:00Z' },
      joinRequest({ requestedAt: 'soon' }),
      joinRequest(),
    ];

    // Act / Assert
    expect(toJoinRequests(raw)).toHaveLength(1);
  });

  it('treats anything that is not a list as empty', () => {
    expect(toJoinRequests({ requests: [] })).toEqual([]);
    expect(toJoinRequests(null)).toEqual([]);
  });
});
