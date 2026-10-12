import { type ReactNode, useEffect, useRef } from 'react';

/**
 * The outcome of a form that replaced the form: a status region whose heading takes focus, so a screen reader reads
 * the outcome and a keyboard user continues from it.
 */
export function Confirmation({ title, children }: { title: string; children: ReactNode }) {
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    heading.current?.focus();
  }, []);
  return (
    <div role="status" className="grid gap-4">
      <h1 ref={heading} tabIndex={-1} className="text-2xl font-semibold outline-none">
        {title}
      </h1>
      {children}
    </div>
  );
}
