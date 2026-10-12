import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ErrorSummary } from './ErrorSummary';
import { TextField } from './TextField';

describe('TextField', () => {
  it('labels the input, ties its hint and error to it and flags it invalid', () => {
    // Arrange / Act
    render(
      <TextField
        id="email"
        label="Email"
        hint="Shown to nobody"
        error="Enter a valid email"
        defaultValue=""
        onChange={() => undefined}
      />,
    );

    // Assert
    const input = screen.getByLabelText('Email');
    expect(input).toBeInvalid();
    expect(input).toHaveAccessibleDescription('Shown to nobody Enter a valid email');
  });

  it('is valid and described by its hint alone when there is no error', () => {
    // Arrange / Act
    render(
      <TextField id="phone" label="Phone" hint="With country code" onChange={() => undefined} />,
    );

    // Assert
    const input = screen.getByLabelText('Phone');
    expect(input).toBeValid();
    expect(input).toHaveAccessibleDescription('With country code');
  });
});

describe('ErrorSummary', () => {
  it('lists two identical messages without a duplicate-key warning', () => {
    // Arrange
    const error = vi.spyOn(console, 'error').mockImplementation(() => undefined);

    // Act
    render(<ErrorSummary problems={{ fields: {}, general: ['Same', 'Same'] }} labels={{}} />);

    // Assert
    expect(screen.getAllByText('Same')).toHaveLength(2);
    expect(error).not.toHaveBeenCalled();
  });

  it('renders nothing without problems', () => {
    // Arrange / Act
    render(<ErrorSummary problems={null} labels={{}} />);

    // Assert
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('takes focus, lists the fields and moves focus to the one chosen', async () => {
    // Arrange
    const user = userEvent.setup();
    render(
      <>
        <label htmlFor="email">Email</label>
        <input id="email" />
        <ErrorSummary
          problems={{
            fields: { email: 'The email address is not valid' },
            general: ['Try later'],
            requestId: 'req-9',
          }}
          labels={{ email: 'Email' }}
        />
      </>,
    );

    // Act
    const alert = screen.getByRole('alert');
    const focusedAtFirst = document.activeElement === alert;
    await user.click(screen.getByRole('button', { name: 'Email: The email address is not valid' }));

    // Assert
    expect(focusedAtFirst).toBe(true);
    expect(alert).toHaveTextContent('Try later');
    expect(alert).toHaveTextContent('Reference: req-9');
    expect(screen.getByLabelText('Email')).toHaveFocus();
  });

  it('takes focus again when a new set of problems arrives', () => {
    // Arrange
    const focus = vi.spyOn(HTMLElement.prototype, 'focus');
    const first = { fields: {}, general: ['One'] };
    const { rerender } = render(<ErrorSummary problems={first} labels={{}} />);
    const callsAfterFirst = focus.mock.calls.length;

    // Act
    rerender(<ErrorSummary problems={{ fields: {}, general: ['Two'] }} labels={{}} />);

    // Assert
    expect(focus.mock.calls.length).toBeGreaterThan(callsAfterFirst);
  });
});
