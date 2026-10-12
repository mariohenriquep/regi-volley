import { describe, expect, it } from 'vitest';
import {
  ApiError,
  GENERIC_ERROR_MESSAGE,
  NETWORK_ERROR_MESSAGE,
  networkError,
  toApiError,
  unwrap,
} from './errors';

const response = (status: number, headers: Record<string, string> = {}) =>
  new Response(null, { status, headers });

describe('toApiError', () => {
  it('keeps code, the API message, fields and request id of a rule violation', () => {
    // Arrange
    const body = {
      code: 'INVALID_FIELD',
      message: 'The password is too short',
      fields: ['password'],
      requestId: 'req-1',
    };

    // Act
    const error = toApiError(response(422), body);

    // Assert
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(422);
    expect(error.code).toBe('INVALID_FIELD');
    expect(error.message).toBe('The password is too short');
    expect(error.fields).toEqual(['password']);
    expect(error.requestId).toBe('req-1');
  });

  it('shows the API message of other client errors', () => {
    // Arrange
    const body = {
      code: 'INVALID_CREDENTIALS',
      message: 'Invalid email or password',
      requestId: 'r',
    };

    // Act
    const error = toApiError(response(401), body);

    // Assert
    expect(error.code).toBe('INVALID_CREDENTIALS');
    expect(error.message).toBe('Invalid email or password');
    expect(error.fields).toEqual([]);
  });

  it('shows generic text for a server error but keeps the request id for support', () => {
    // Arrange
    const body = {
      code: 'INTERNAL_ERROR',
      message: 'An unexpected error occurred',
      requestId: 'req-9',
    };

    // Act
    const error = toApiError(response(500), body);

    // Assert
    expect(error.code).toBe('INTERNAL_ERROR');
    expect(error.message).toBe(GENERIC_ERROR_MESSAGE);
    expect(error.requestId).toBe('req-9');
  });

  it('copes with a body that is not the API error shape', () => {
    // Arrange
    const body = '<html>Bad gateway</html>';

    // Act
    const error = toApiError(response(502), body);

    // Assert
    expect(error.status).toBe(502);
    expect(error.code).toBe('HTTP_502');
    expect(error.message).toBe(GENERIC_ERROR_MESSAGE);
    expect(error.requestId).toBeUndefined();
  });

  it('ignores fields that are not a list of strings', () => {
    // Arrange
    const body = { code: 'INVALID_REQUEST', message: 'Invalid request', fields: 'email' };

    // Act
    const error = toApiError(response(400), body);

    // Assert
    expect(error.fields).toEqual([]);
  });

  it('reads Retry-After of a throttled call', () => {
    // Arrange
    const body = { code: 'TOO_MANY_REQUESTS', message: 'Too many requests, try again later' };

    // Act
    const error = toApiError(response(429, { 'Retry-After': '120' }), body);

    // Assert
    expect(error.retryAfterSeconds).toBe(120);
    expect(error.message).toBe('Too many requests, try again later');
  });
});

describe('networkError', () => {
  it('reports an unreachable server', () => {
    // Arrange
    const cause = new TypeError('Failed to fetch');

    // Act
    const error = networkError(cause);

    // Assert
    expect(error.status).toBe(0);
    expect(error.code).toBe('NETWORK_ERROR');
    expect(error.message).toBe(NETWORK_ERROR_MESSAGE);
  });
});

describe('unwrap', () => {
  it('returns the data of a successful call', async () => {
    // Arrange
    const call = Promise.resolve({ data: { memberId: 'm-1' }, response: response(200) });

    // Act
    const data = await unwrap(call);

    // Assert
    expect(data).toEqual({ memberId: 'm-1' });
  });

  it('throws the mapped ApiError of a failed call', async () => {
    // Arrange
    const call = Promise.resolve({
      error: { code: 'NOT_FOUND', message: 'The requested resource was not found' },
      response: response(404),
    });

    // Act
    const failure = unwrap(call);

    // Assert
    await expect(failure).rejects.toMatchObject({ status: 404, code: 'NOT_FOUND' });
  });

  it('turns a failed fetch into a network error', async () => {
    // Arrange
    const call = Promise.reject(new TypeError('Failed to fetch'));

    // Act
    const failure = unwrap(call);

    // Assert
    await expect(failure).rejects.toMatchObject({ code: 'NETWORK_ERROR' });
  });
});
