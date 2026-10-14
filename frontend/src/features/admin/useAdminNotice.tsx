import { type ReactNode, useEffect, useRef, useState } from 'react';
import { Notice } from '@/features/sessions/Notice';

export interface NoticeContent {
  /** What the API did, in words. May hold a link. */
  text: ReactNode;
  tone?: 'info' | 'warning';
}

/**
 * The outcome notice of the admin screens and the focus rules around their dialogs, as the Coaching screens have them: the
 * dialogs have no Radix trigger, so the control that opened one is remembered and gets focus back when the dialog closes;
 * after an outcome the notice (`role=status`) gets it instead, because the control may be gone (an approved request leaves the
 * list). Render `notice` where messages go and pass `onCloseAutoFocus` to every dialog.
 */
export function useAdminNotice() {
  const [content, setContent] = useState<NoticeContent | null>(null);
  const noticeRef = useRef<HTMLDivElement>(null);
  const opener = useRef<HTMLElement | null>(null);

  // A new message is announced and focused, so a keyboard or screen-reader user lands on the outcome.
  useEffect(() => {
    if (content) noticeRef.current?.focus();
  }, [content]);

  const notice: ReactNode = content && (
    <Notice
      ref={noticeRef}
      tone={content.tone}
      onDismiss={() => {
        setContent(null);
      }}
    >
      {content.text}
    </Notice>
  );

  return {
    notice,
    announce: (next: NoticeContent) => {
      setContent(next);
    },
    clear: () => {
      setContent(null);
    },
    /** Called when a dialog opens: forgets the last outcome and remembers where focus goes back to. */
    remember: (control: HTMLElement) => {
      opener.current = control;
      setContent(null);
    },
    onCloseAutoFocus: (event: Event) => {
      event.preventDefault();
      if (noticeRef.current) noticeRef.current.focus();
      else opener.current?.focus();
    },
  };
}

export type AdminNotice = ReturnType<typeof useAdminNotice>;
