import { describe, expect, it } from 'vitest';
import { toMe } from './useMe';

describe('toMe', () => {
  it('keeps the roles the API lists, once each, in its order', () => {
    // Arrange
    const raw = { roles: ['MEMBER', 'COACH', 'COACH', 'ADMIN'] } as const;

    // Act
    const me = toMe({ ...raw, roles: [...raw.roles] });

    // Assert
    expect(me.roles).toEqual(['MEMBER', 'COACH', 'ADMIN']);
  });

  it('drops a role it does not know and reads a missing or malformed list as no roles', () => {
    // Arrange
    const unknown = { roles: ['MEMBER', 'REFEREE'] } as unknown as Parameters<typeof toMe>[0];
    const malformed = { roles: 'ADMIN' } as unknown as Parameters<typeof toMe>[0];

    // Act / Assert
    expect(toMe(unknown).roles).toEqual(['MEMBER']);
    expect(toMe(malformed).roles).toEqual([]);
    expect(toMe({}).roles).toEqual([]);
  });
});
