'use client';

import { useEffect, useState } from 'react';

const focusClassName = 'focus-visible:outline-3 focus-visible:outline-offset-3 focus-visible:outline-[#5147ff]';

export default function CopyPlaceLinkButton({ label, path }: { label: string; path?: string }) {
  const [feedback, setFeedback] = useState<{ copied: boolean; url: string } | null>(null);
  const [copying, setCopying] = useState(false);

  useEffect(() => {
    if (!feedback?.copied) return;
    const timer = setTimeout(() => setFeedback(null), 3000);
    return () => clearTimeout(timer);
  }, [feedback]);

  async function copyLink() {
    // 클릭 시 주소를 읽어서 마지막 지도 이동과 필터를 반영합니다.
    const url = path ? new URL(path, window.location.origin).href : window.location.href;
    setCopying(true);
    setFeedback(null);
    try {
      await navigator.clipboard.writeText(url);
      setFeedback({ copied: true, url });
    } catch {
      // 클립보드 권한이 없거나 지원하지 않는 브라우저에서도 직접 복사할 수 있습니다.
      setFeedback({ copied: false, url });
    } finally {
      setCopying(false);
    }
  }

  return (
    <div className="flex max-w-full flex-col items-start gap-1.5">
      <button
        className={`cursor-pointer rounded-lg border border-[#ddd9ff] bg-white px-3 py-[9px] text-xs/[1.4] font-bold
          whitespace-nowrap text-[#5147ff] disabled:cursor-wait disabled:opacity-60 ${focusClassName}`}
        type="button"
        onClick={copyLink}
        disabled={copying}
      >
        {copying ? '복사 중…' : label}
      </button>
      <span className="text-[11px]/[1.6] text-[#6156cc] empty:hidden" role="status">
        {feedback && (feedback.copied ? '링크를 복사했어요.' : '복사하지 못했어요. 아래 주소를 직접 복사해 주세요.')}
      </span>
      {feedback && !feedback.copied && (
        <input
          className={`w-[260px] max-w-full min-w-0 rounded-md border border-[#dfe4ed] bg-[#fafbfe] p-2 text-xs text-[#16243b] ${focusClassName}`}
          aria-label="공유할 링크"
          readOnly
          value={feedback.url}
          onFocus={event => event.currentTarget.select()}
        />
      )}
    </div>
  );
}
