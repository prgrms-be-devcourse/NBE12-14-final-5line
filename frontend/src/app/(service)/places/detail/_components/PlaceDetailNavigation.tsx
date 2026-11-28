'use client';
// Lucian UX/UI Prompt #416 · MIT (c) 2026 Seokwoooo; see THIRD_PARTY_NOTICES.md.
import { useEffect, useState } from 'react';
const sections = [['place-introduction','장소 정보'],['place-notices','공지사항'],['place-reviews-title','리뷰']] as const;
export default function PlaceDetailNavigation() {
  const [active, setActive] = useState<string>('place-introduction');
  useEffect(() => {
    if (typeof IntersectionObserver === 'undefined') return;
    const observer = new IntersectionObserver(entries => {
      const entry = entries.find(entry => entry.isIntersecting);
      if (entry) setActive(entry.target.id);
    }, { rootMargin: '-15% 0px -65% 0px' });
    sections.forEach(([id]) => { const target = document.getElementById(id); if(target)observer.observe(target); });
    return () => observer.disconnect();
  }, []);
  return <nav aria-label="상세 정보 메뉴" className="sticky top-[85px] z-10 mb-7 flex gap-6 overflow-x-auto border-b border-[#ececf2] bg-white py-2 text-sm font-semibold whitespace-nowrap min-[1101px]:top-[93px]">
    {sections.map(([id,label]) => <a key={id} href={`#${id}`} onClick={() => setActive(id)} aria-current={active===id ? 'location' : undefined} className="border-b-2 border-transparent px-2 py-3 text-slate-500 aria-[current=location]:border-[#5942FF] aria-[current=location]:text-[#5942FF]">{label}</a>)}
  </nav>;
}
