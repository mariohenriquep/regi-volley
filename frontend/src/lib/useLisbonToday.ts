import { useEffect, useState } from 'react';
import { lisbonDay } from './lisbon';

/**
 * Today's calendar day in Lisbon. It is read again when the app comes back to the foreground, so an installed app that
 * stayed open overnight does not keep showing yesterday's week.
 */
export function useLisbonToday(): string {
  const [today, setToday] = useState(() => lisbonDay(new Date()));
  useEffect(() => {
    const refresh = () => {
      if (document.visibilityState === 'visible') setToday(lisbonDay(new Date()));
    };
    document.addEventListener('visibilitychange', refresh);
    return () => {
      document.removeEventListener('visibilitychange', refresh);
    };
  }, []);
  return today;
}
