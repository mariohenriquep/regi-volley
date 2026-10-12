/**
 * Whether a new version of the app is waiting, and how to switch to it. With `registerType: 'prompt'` the service
 * worker never takes over by itself: the user chooses when, so a form half-filled in is never reloaded away.
 */
export interface UpdateStore {
  subscribe: (listener: () => void) => () => void;
  /** True while a new version waits and the user has not put it off. */
  getSnapshot: () => boolean;
  /** The service worker reports a waiting version; `apply` activates it and reloads the open pages. */
  offer: (apply: () => Promise<void>) => void;
  /** Activates the waiting version and reloads. */
  apply: () => Promise<void>;
  /** Hides the notice until the next version or the next visit. */
  dismiss: () => void;
}

export function createUpdateStore(): UpdateStore {
  let pending: (() => Promise<void>) | null = null;
  let visible = false;
  const listeners = new Set<() => void>();
  const notify = () => {
    listeners.forEach((listener) => {
      listener();
    });
  };
  return {
    subscribe: (listener) => {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
    getSnapshot: () => visible,
    offer: (apply) => {
      pending = apply;
      visible = true;
      notify();
    },
    apply: async () => {
      await pending?.();
    },
    dismiss: () => {
      visible = false;
      notify();
    },
  };
}
