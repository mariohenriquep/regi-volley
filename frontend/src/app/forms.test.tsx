import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { publicAssociation, refreshRejected } from '@/test/handlers';
import { renderApp } from '@/test/render';
import { server } from '@/test/server';

describe('every form', () => {
  it.each([
    ['/login', 'Sign in'],
    ['/register', 'Register'],
    ['/a/lisbon-spikers/join', 'Send request'],
    ['/forgot-password', 'Send reset link'],
    ['/activate#token=t', 'Activate account'],
    ['/reset-password#token=t', 'Change password'],
  ])(
    '%s posts if it is ever submitted without the script, so nothing typed reaches an address',
    async (route, button) => {
      // Arrange
      server.use(
        refreshRejected,
        http.get('/api/v1/public/associations/:shortName', () =>
          HttpResponse.json(publicAssociation),
        ),
      );
      window.history.replaceState(null, '', route);

      // Act
      renderApp(route.split('#')[0] ?? route);
      const submit = await screen.findByRole('button', { name: button });

      // Assert
      expect(submit.closest('form')).toHaveAttribute('method', 'post');
    },
  );
});
