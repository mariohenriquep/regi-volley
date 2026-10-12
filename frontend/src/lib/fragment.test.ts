import { describe, expect, it, vi } from 'vitest';
import { takeFragmentToken, tokenFromHash } from './fragment';

const visit = (url: string, state: unknown = null) => {
  window.history.replaceState(state, '', url);
};

describe('takeFragmentToken', () => {
  it('reads the token and removes it from the address bar', () => {
    // Arrange
    visit('/activate#token=abc123');

    // Act
    const token = takeFragmentToken();

    // Assert
    expect(token).toBe('abc123');
    expect(window.location.hash).toBe('');
    expect(window.location.href).not.toContain('abc123');
    expect(window.location.pathname).toBe('/activate');
  });

  it('decodes a percent-encoded token', () => {
    // Arrange
    visit('/reset-password#token=a%2Bb%2Fc%3D');

    // Act
    const token = takeFragmentToken();

    // Assert
    expect(token).toBe('a+b/c=');
  });

  it('keeps the path, the query string and the history state', () => {
    // Arrange
    const routerState = { usr: null, key: 'k1', idx: 0 };
    visit('/reset-password?from=mail#token=t-1', routerState);

    // Act
    takeFragmentToken();

    // Assert
    expect(window.location.pathname + window.location.search).toBe('/reset-password?from=mail');
    expect(window.history.state).toEqual(routerState);
  });

  it('finds the token among other fragment parameters', () => {
    // Arrange
    visit('/activate#lang=en&token=xyz');

    // Act
    const token = takeFragmentToken();

    // Assert
    expect(token).toBe('xyz');
    expect(window.location.hash).toBe('');
  });

  it('scrubs a fragment that has no token and returns null', () => {
    // Arrange
    visit('/activate#something-else');

    // Act
    const token = takeFragmentToken();

    // Assert
    expect(token).toBeNull();
    expect(window.location.hash).toBe('');
  });

  it('returns null for an empty token', () => {
    // Arrange
    visit('/activate#token=');

    // Act
    const token = takeFragmentToken();

    // Assert
    expect(token).toBeNull();
  });

  it('returns null for a malformed token but still scrubs it', () => {
    // Arrange
    visit('/activate#token=%E0%A4%A');

    // Act
    const token = takeFragmentToken();

    // Assert
    expect(token).toBeNull();
    expect(window.location.hash).toBe('');
  });

  it('leaves history alone when there is no fragment', () => {
    // Arrange
    visit('/activate');
    const replaceState = vi.spyOn(window.history, 'replaceState');

    // Act
    const token = takeFragmentToken();

    // Assert
    expect(token).toBeNull();
    expect(replaceState).not.toHaveBeenCalled();
  });
});

describe('tokenFromHash', () => {
  it.each([
    ['#token=abc', 'abc'],
    ['#a=1&token=a%2Bb', 'a+b'],
    ['#other=1', null],
    ['', null],
    ['#token=%E0%A4%A', null],
  ])('reads %s as %s', (hash, expected) => {
    // Arrange / Act
    const token = tokenFromHash(hash);

    // Assert
    expect(token).toBe(expected);
  });
});
