import type { UpdateStore } from './store';

/** The part of `registerSW` (`virtual:pwa-register`) this app uses. */
export type RegisterServiceWorker = (options: {
  immediate?: boolean;
  onNeedRefresh?: () => void;
}) => (reloadPage?: boolean) => Promise<void>;

/**
 * Registers the service worker. A new version is only *offered* to the store; it takes over (and the open pages
 * reload) when the user applies it from the notice.
 */
export function startServiceWorker(store: UpdateStore, register: RegisterServiceWorker): void {
  const update = register({
    immediate: true,
    onNeedRefresh: () => {
      store.offer(() => update(true));
    },
  });
}
