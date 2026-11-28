'use client';
import Image from 'next/image';
import { useId, useState } from 'react';
export default function KakaoDirectionsButton({ place }: { place: { name: string | null; latitude: number | null; longitude: number | null } }) {
  const tooltipId = useId();
  const [dismissed, setDismissed] = useState(false);
  if (place.latitude === null || place.longitude === null || !Number.isFinite(place.latitude) || !Number.isFinite(place.longitude)) return null;
  return <span className="group relative shrink-0" onMouseEnter={() => setDismissed(false)} onFocusCapture={() => setDismissed(false)} onKeyDown={event => { if (event.key === 'Escape') setDismissed(true); }}>
    <a href={`https://map.kakao.com/link/to/${encodeURIComponent(place.name ?? '장소')},${place.latitude},${place.longitude}`} target="_blank" rel="noopener noreferrer" aria-label={`${place.name ?? '장소'} 카카오맵 길찾기 (새 창)`} aria-describedby={dismissed ? undefined : tooltipId} className="flex size-9 items-center justify-center rounded-lg border border-[#eadc7a] bg-[#fffbea] no-underline hover:bg-[#fff3b0] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#5147ff]">
      <Image src="/icons/kakaomap.ico" alt="" width={16} height={16} unoptimized aria-hidden="true"/>
    </a>
    {!dismissed && <span id={tooltipId} role="tooltip" className="invisible absolute right-0 bottom-full z-20 pb-2 opacity-0 group-focus-within:visible group-focus-within:opacity-100 group-hover:visible group-hover:opacity-100"><span className="block rounded-lg bg-[#17172B] px-3 py-2 text-[11px] whitespace-nowrap text-white">카카오맵 길찾기</span></span>}
  </span>;
}
