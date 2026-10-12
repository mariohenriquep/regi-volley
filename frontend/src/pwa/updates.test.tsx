import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { UpdateContext } from './context';
import { startServiceWorker } from './register';
import { createUpdateStore } from './store';
import { UpdatePrompt } from './UpdatePrompt';

describe('update prompt', () => {
  it('shows nothing until a new version is waiting', () => {
    // Arrange
    const store = createUpdateStore();

    // Act
    render(
      <UpdateContext.Provider value={store}>
        <UpdatePrompt />
      </UpdateContext.Provider>,
    );

    // Assert
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('offers the reload when a new version is waiting, and applies it only when asked', async () => {
    // Arrange
    const user = userEvent.setup();
    const store = createUpdateStore();
    const apply = vi.fn(() => Promise.resolve());
    render(
      <UpdateContext.Provider value={store}>
        <UpdatePrompt />
      </UpdateContext.Provider>,
    );

    // Act
    act(() => {
      store.offer(apply);
    });
    const notice = await screen.findByRole('status');
    const appliedBeforeClick = apply.mock.calls.length;
    await user.click(screen.getByRole('button', { name: 'Reload' }));

    // Assert
    expect(notice).toHaveTextContent('A new version is available — reload');
    expect(appliedBeforeClick).toBe(0);
    expect(apply).toHaveBeenCalledOnce();
  });

  it('can be put off, which hides it without updating', async () => {
    // Arrange
    const user = userEvent.setup();
    const store = createUpdateStore();
    const apply = vi.fn(() => Promise.resolve());
    render(
      <UpdateContext.Provider value={store}>
        <UpdatePrompt />
      </UpdateContext.Provider>,
    );
    act(() => {
      store.offer(apply);
    });

    // Act
    await user.click(await screen.findByRole('button', { name: 'Later' }));

    // Assert
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(apply).not.toHaveBeenCalled();
  });
});

describe('startServiceWorker', () => {
  it('registers the worker and offers the update instead of applying it', () => {
    // Arrange
    const store = createUpdateStore();
    const reload = vi.fn(() => Promise.resolve());
    let needRefresh: (() => void) | undefined;
    const register = vi.fn((options: { immediate?: boolean; onNeedRefresh?: () => void }) => {
      needRefresh = options.onNeedRefresh;
      return reload;
    });

    // Act
    startServiceWorker(store, register);
    const before = { snapshot: store.getSnapshot(), reloads: reload.mock.calls.length };
    needRefresh?.();

    // Assert
    expect(register).toHaveBeenCalledWith(expect.objectContaining({ immediate: true }));
    expect(before).toEqual({ snapshot: false, reloads: 0 });
    expect(store.getSnapshot()).toBe(true);
    expect(reload).not.toHaveBeenCalled();
  });

  it('reloads every page with the new version only when the user applies it', async () => {
    // Arrange
    const store = createUpdateStore();
    const reload = vi.fn(() => Promise.resolve());
    startServiceWorker(store, (options) => {
      options.onNeedRefresh?.();
      return reload;
    });

    // Act
    await store.apply();

    // Assert
    expect(reload).toHaveBeenCalledWith(true);
  });
});
