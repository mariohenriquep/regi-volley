import { describe, expect, it } from 'vitest';
import { ApiError, GENERIC_ERROR_MESSAGE } from '@/api/errors';
import { fromApiError, fromClient, normaliseFieldName, waitText } from './problems';

const FIELD_MAP = { shortname: 'shortName', name: 'founderName', levelnames: 'levels' };

describe('waitText', () => {
  it.each([
    [undefined, 'a little while'],
    [0, 'a little while'],
    [1, '1 second'],
    [45, '45 seconds'],
    [60, '1 minute'],
    [61, '2 minutes'],
    [900, '15 minutes'],
    [3600, '1 hour'],
    [86_400, '24 hours'],
  ])('says how long to wait for %s seconds', (seconds, expected) => {
    // Arrange / Act
    const text = waitText(seconds);

    // Assert
    expect(text).toBe(expected);
  });
});

describe('normaliseFieldName', () => {
  it.each([
    ['shortName', 'shortname'],
    ['short name', 'shortname'],
    ['levelNames[2]', 'levelnames'],
    ['NIF', 'nif'],
    ['policy version', 'policyversion'],
  ])('maps %s to %s, so the 400 and 422 spellings meet', (name, expected) => {
    // Arrange / Act
    const normalised = normaliseFieldName(name);

    // Assert
    expect(normalised).toBe(expected);
  });
});

describe('fromApiError', () => {
  it("attaches a 422's message to the control its field maps to", () => {
    // Arrange
    const error = new ApiError(
      422,
      'INVALID_FIELD',
      'The short name is required',
      ['short name'],
      'r-1',
    );

    // Act
    const problems = fromApiError(error, FIELD_MAP);

    // Assert
    expect(problems.fields).toEqual({ shortName: 'The short name is required' });
    expect(problems.general).toEqual([]);
    expect(problems.requestId).toBe('r-1');
  });

  it("marks the fields of a 400 without quoting the API's generic message", () => {
    // Arrange
    const error = new ApiError(400, 'INVALID_REQUEST', 'Invalid request', [
      'levelNames[0]',
      'name',
    ]);

    // Act
    const problems = fromApiError(error, FIELD_MAP);

    // Assert
    expect(Object.keys(problems.fields).sort()).toEqual(['founderName', 'levels']);
    expect(Object.values(problems.fields).join(' ')).not.toContain('Invalid request');
  });

  it('keeps the message of a field it cannot place in the summary', () => {
    // Arrange
    const error = new ApiError(422, 'INVALID_FIELD', 'The email address is not valid', ['email']);

    // Act
    const problems = fromApiError(error, FIELD_MAP);

    // Assert
    expect(problems.fields).toEqual({});
    expect(problems.general).toEqual(['The email address is not valid']);
  });

  it('shows a rule violation without a field as a general message', () => {
    // Arrange
    const error = new ApiError(422, 'BUSINESS_RULE_VIOLATION', 'Consent is required');

    // Act
    const problems = fromApiError(error, FIELD_MAP);

    // Assert
    expect(problems.general).toEqual(['Consent is required']);
  });

  it('turns a 429 into a polite wait', () => {
    // Arrange
    const error = new ApiError(429, 'TOO_MANY_REQUESTS', 'Too many requests', [], 'r-2', 900);

    // Act
    const problems = fromApiError(error, FIELD_MAP);

    // Assert
    expect(problems.general).toEqual(['Too many attempts. Please wait 15 minutes and try again.']);
  });

  it('shows generic text for anything that is not an ApiError', () => {
    // Arrange / Act
    const problems = fromApiError(new TypeError('boom'), FIELD_MAP);

    // Assert
    expect(problems.general).toEqual([GENERIC_ERROR_MESSAGE]);
  });
});

describe('fromClient', () => {
  it('collects the control errors found before sending', () => {
    // Arrange / Act
    const problems = fromClient({ email: 'Enter your email address' });

    // Assert
    expect(problems.fields).toEqual({ email: 'Enter your email address' });
    expect(problems.general).toEqual([]);
  });
});
