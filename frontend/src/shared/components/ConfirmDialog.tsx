'use client';
// Lucian UX/UI Prompt #436 · MIT (c) 2026 Seokwoooo; see THIRD_PARTY_NOTICES.md.
import { useEffect, useRef } from 'react';
export default function ConfirmDialog({ open, busy, onCancel, onConfirm }: { open: boolean; busy: boolean; onCancel: () => void; onConfirm: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const cancel = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if(open) { dialog.current?.showModal(); cancel.current?.focus(); }
    else if(dialog.current?.open) dialog.current.close();
  }, [open]);
  return <dialog ref={dialog} role="alertdialog" aria-labelledby="delete-review-title" aria-describedby="delete-review-description" onCancel={event => { if(busy)event.preventDefault(); else onCancel(); }} className="fixed m-auto w-[min(400px,calc(100vw-40px))] rounded-2xl border-0 bg-white p-6 text-[#17172B] backdrop:bg-[#17172B]/40">
    <h2 id="delete-review-title" className="text-lg font-semibold">후기를 삭제할까요?</h2>
    <p id="delete-review-description" className="mt-3 text-sm leading-6 text-slate-500">삭제한 후기와 사진은 복구할 수 없어요.</p>
    <div className="mt-6 flex justify-end gap-3"><button ref={cancel} type="button" disabled={busy} onClick={onCancel} className="min-h-11 rounded-full bg-[#F5F6F8] px-5 text-sm font-semibold">취소</button><button type="button" disabled={busy} onClick={onConfirm} className="min-h-11 rounded-full bg-red-600 px-5 text-sm font-semibold text-white disabled:opacity-50">{busy?'삭제 중…':'삭제 확인'}</button></div>
  </dialog>;
}
