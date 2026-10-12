import { createContext, useContext } from 'react';
import { createUpdateStore, type UpdateStore } from './store';

/** Without a provider (tests, Storybook-like uses) no update is ever offered. */
export const UpdateContext = createContext<UpdateStore>(createUpdateStore());

export const useUpdates = (): UpdateStore => useContext(UpdateContext);
