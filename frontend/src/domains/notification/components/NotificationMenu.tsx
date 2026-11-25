'use client';

import { Bell, X } from 'lucide-react';
import Link from 'next/link';
import { useId, useRef, useState } from 'react';
import NotificationList from './NotificationList';
import NotificationUnreadIndicator from './NotificationUnreadIndicator';

export default function NotificationMenu({ memberId, iconClassName }: { memberId: number; iconClassName: string }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [isOpen, setIsOpen] = useState(false);
  const titleId = useId();
  const dialogId = useId();
  const unreadDescriptionId = useId();

  function closeDialog() {
    dialog.current?.close();
  }

  return (
    <>
      <button type="button" className={`${iconClassName} relative cursor-pointer`} aria-label="알림함"
        aria-describedby={unreadDescriptionId}
        aria-haspopup="dialog" aria-controls={dialogId} onClick={() => {
          dialog.current?.showModal();
          setIsOpen(true);
        }}>
        <Bell size={22} strokeWidth={1.8} aria-hidden="true" />
        <span className="absolute top-2 right-1.5 flex">
          <NotificationUnreadIndicator memberId={memberId} descriptionId={unreadDescriptionId} />
        </span>
      </button>
      <dialog ref={dialog} id={dialogId} aria-labelledby={titleId} closedby="any" onClose={() => setIsOpen(false)}
        className="m-auto max-h-[calc(100%-2rem)] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-2xl bg-white p-5 text-[#16243b] shadow-xl backdrop:bg-slate-950/50 sm:p-6">
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 id={titleId} className="text-xl font-bold">읽지 않은 알림</h2>
          <button type="button" aria-label="알림함 닫기" className={`${iconClassName} cursor-pointer`} onClick={closeDialog}>
            <X size={20} aria-hidden="true" />
          </button>
        </div>
        {isOpen && <NotificationList memberId={memberId} unreadOnly onNavigate={closeDialog} />}
        <div className="mt-5 border-t border-[#e9edf3] pt-4">
          <Link href="/mypage/notifications/" className="text-sm font-bold text-[#5147d9] hover:underline"
            onNavigate={closeDialog}>전체 알림함 보기 <span aria-hidden="true">→</span></Link>
        </div>
      </dialog>
    </>
  );
}
