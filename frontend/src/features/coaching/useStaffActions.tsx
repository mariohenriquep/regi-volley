import { type ReactNode, useEffect, useRef, useState } from 'react';
import { Notice } from '@/features/sessions/Notice';
import { CancelSessionDialog } from './CancelSessionDialog';
import { ChangeSeatsDialog } from './ChangeSeatsDialog';
import { cancelledNotice, capacityNotice } from './messages';
import type { CoachingSession } from './model';

interface NoticeContent {
  text: string;
  tone: 'info' | 'warning';
}

/**
 * What the Coaching screens share: the two staff dialogs, the notice that reports an outcome and the focus rules around
 * them. The dialogs have no Radix trigger, so the control that opened one is remembered and gets focus back when the
 * dialog closes; after an outcome the notice gets it instead (the control may be gone: a cancelled session has no
 * buttons left). Render `notice` where messages go and `dialogs` once.
 */
export function useStaffActions() {
  const [cancelling, setCancelling] = useState<CoachingSession | null>(null);
  const [changing, setChanging] = useState<CoachingSession | null>(null);
  const [notice, setNotice] = useState<NoticeContent | null>(null);
  const noticeRef = useRef<HTMLDivElement>(null);
  const opener = useRef<HTMLElement | null>(null);

  // A new message is announced (`role=status`) and focused, so a keyboard or screen-reader user lands on the outcome.
  useEffect(() => {
    if (notice) noticeRef.current?.focus();
  }, [notice]);

  const handleCloseAutoFocus = (event: Event) => {
    event.preventDefault();
    if (noticeRef.current) noticeRef.current.focus();
    else opener.current?.focus();
  };

  const announce = (content: NoticeContent) => {
    setNotice(content);
  };

  const noticeElement: ReactNode = notice && (
    <Notice
      ref={noticeRef}
      tone={notice.tone}
      onDismiss={() => {
        setNotice(null);
      }}
    >
      {notice.text}
    </Notice>
  );

  const dialogs: ReactNode = (
    <>
      <CancelSessionDialog
        session={cancelling}
        onClose={() => {
          setCancelling(null);
        }}
        onCancelled={(outcome) => {
          announce({ text: cancelledNotice(outcome), tone: 'info' });
          setCancelling(null);
        }}
        onCloseAutoFocus={handleCloseAutoFocus}
      />
      <ChangeSeatsDialog
        session={changing}
        onClose={() => {
          setChanging(null);
        }}
        onChanged={(outcome) => {
          announce({ text: capacityNotice(outcome), tone: 'info' });
          setChanging(null);
        }}
        onCloseAutoFocus={handleCloseAutoFocus}
      />
    </>
  );

  return {
    notice: noticeElement,
    dialogs,
    announce,
    clearNotice: () => {
      setNotice(null);
    },
    openCancel: (session: CoachingSession, button: HTMLElement) => {
      opener.current = button;
      setNotice(null);
      setCancelling(session);
    },
    openChangeSeats: (session: CoachingSession, button: HTMLElement) => {
      opener.current = button;
      setNotice(null);
      setChanging(session);
    },
  };
}
