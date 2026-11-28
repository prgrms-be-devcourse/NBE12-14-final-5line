'use client';
import { Check, X } from 'lucide-react';
/** One polite announcement; does not move focus away from the completed action. */
export default function ActionToast({ message, onDismiss }: { message: string; onDismiss: () => void }) {
  return <div className="fixed inset-x-4 bottom-[calc(env(safe-area-inset-bottom)+80px)] z-[100] flex items-center gap-3 rounded-xl border border-[#e5e3ef] bg-[#17172B] px-4 py-3 text-sm text-white shadow-lg sm:inset-x-auto sm:right-6 sm:bottom-6 sm:max-w-sm">
    <Check size={18} aria-hidden="true" className="shrink-0 text-[#bcb2ff]"/>
    <p role="status" aria-live="polite" className="min-w-0 flex-1">{message}</p>
    <button type="button" onClick={onDismiss} aria-label="알림 닫기" className="flex size-8 shrink-0 items-center justify-center rounded-lg hover:bg-white/10 focus-visible:outline-2 focus-visible:outline-white"><X size={16} aria-hidden="true"/></button>
  </div>;
}
