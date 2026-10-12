import { describe, expect, it } from 'vitest';
import { accountLinkLoader, createTokenHolder } from './accountLink';

describe('createTokenHolder', () => {
  it('gives the token once and then nothing', () => {
    // Arrange
    const holder = createTokenHolder('secret');

    // Act
    const first = holder.take();
    const second = holder.take();

    // Assert
    expect(first).toBe('secret');
    expect(second).toBeNull();
  });

  it('holds nothing for a link without a token', () => {
    // Arrange / Act
    const token = createTokenHolder(null).take();

    // Assert
    expect(token).toBeNull();
  });
});

describe('accountLinkLoader', () => {
  it('scrubs the fragment and hands over a one-shot holder, never the bare token', () => {
    // Arrange
    window.history.replaceState(null, '', '/activate#token=abc');

    // Act
    const data = accountLinkLoader();

    // Assert
    expect(window.location.hash).toBe('');
    expect(JSON.stringify(data)).not.toContain('abc');
    expect(data.link.take()).toBe('abc');
    expect(data.link.take()).toBeNull();
  });
});
