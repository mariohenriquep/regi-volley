import { describe, expect, it } from 'vitest';
import { memberDetail, memberEntry, memberList, levelsAnswer } from '@/test/fixtures';
import { resolveStatus, toLevelOptions, toMemberDetail, toMemberListing } from './model';

describe('resolveStatus', () => {
  it.each([
    ['ACTIVE', 'ACTIVE'],
    ['INACTIVE', 'INACTIVE'],
    ['active', undefined],
    ['ALL', undefined],
    ['', undefined],
    [null, undefined],
  ])('reads %j from the address as %j', (asked, expected) => {
    expect(resolveStatus(asked)).toBe(expected);
  });
});

describe('toMemberListing', () => {
  it('reads the rows, the roles it knows and whether the list was cut', () => {
    // Arrange
    // A newer API may send a role word this version does not know; the type says it cannot, so it is cast.
    const roles = ['MEMBER', 'REFEREE', 'COACH', 'COACH'] as unknown as ['MEMBER'];
    const raw = memberList([memberEntry({ roles })], true);

    // Act
    const listing = toMemberListing(raw);

    // Assert
    expect(listing.truncated).toBe(true);
    expect(listing.members).toHaveLength(1);
    expect(listing.members[0]).toMatchObject({ name: 'Ana Silva', roles: ['MEMBER', 'COACH'] });
  });

  it('skips a row without an id and treats a non-list as empty', () => {
    // Arrange
    const raw = { members: [{ name: 'No id' }, memberEntry()] };

    // Act / Assert
    expect(toMemberListing(raw).members.map((row) => row.name)).toEqual(['Ana Silva']);
    expect(toMemberListing({ members: 'nope' } as never).members).toEqual([]);
  });

  it('says nothing about activation when the API does not', () => {
    expect(
      toMemberListing(memberList([memberEntry({ accountActivated: undefined })])).members[0]
        ?.accountActivated,
    ).toBeUndefined();
  });
});

describe('toMemberDetail', () => {
  it('lists the level changes newest first and drops those without a readable date', () => {
    // Arrange
    const raw = memberDetail({
      levelChanges: [
        { changedAt: '2026-06-10T10:00:00Z', toLevelName: 'Intermediate' },
        { changedAt: 'yesterday', toLevelName: 'Broken' },
        { changedAt: '2026-07-14T10:00:00Z', toLevelName: 'Advanced' },
      ],
    });

    // Act
    const detail = toMemberDetail(raw);

    // Assert
    expect(detail?.levelChanges.map((change) => change.to)).toEqual(['Advanced', 'Intermediate']);
  });

  it('keeps no contact data of an anonymised member, whatever the API sent', () => {
    // Arrange
    const raw = memberDetail({ anonymised: true, email: 'leftover@example.com', phone: '1' });

    // Act
    const detail = toMemberDetail(raw);

    // Assert
    expect(detail).toMatchObject({ anonymised: true, email: undefined, phone: undefined });
  });

  it('is nothing without an id', () => {
    expect(toMemberDetail({ name: 'Nobody' })).toBeNull();
  });

  it('drops a subscription date that is not a calendar day', () => {
    // Arrange
    const raw = memberDetail({
      subscriptions: [
        {
          subscriptionId: '66666666-6666-6666-6666-666666666666',
          startDate: '2026-02-30',
          endDate: '2026-09-29',
        },
      ],
    });

    // Act
    const subscription = toMemberDetail(raw)?.subscriptions[0];

    // Assert
    expect(subscription?.startDate).toBeUndefined();
    expect(subscription?.endDate).toBe('2026-09-29');
  });
});

describe('toLevelOptions', () => {
  it('marks the entry level and keeps the API order', () => {
    expect(toLevelOptions(levelsAnswer()).map(({ name, isEntry }) => [name, isEntry])).toEqual([
      ['Beginner', true],
      ['Intermediate', false],
    ]);
  });
});
