import { useEffect } from 'react';

/** Sets the document title, which screen readers announce when the route changes. */
export function usePageTitle(title: string): void {
  useEffect(() => {
    document.title = `${title} · RegiVolley`;
  }, [title]);
}
