'use client';
// Lucian UX/UI Prompt #061 · MIT (c) 2026 Seokwoooo; see THIRD_PARTY_NOTICES.md.
import { useRef } from 'react';
import { Expand, X } from 'lucide-react';
import PlacePhoto, { getPlacePhoto, PlacePhotoCredit } from '@/domains/place/components/PlacePhoto/PlacePhoto';
import type { PlaceSummary } from '@/domains/place/types/placeApi.type';
export default function PlaceMedia({ place }: { place: PlaceSummary }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const photo = getPlacePhoto(place);
  return <div className="relative overflow-hidden rounded-2xl">
    <PlacePhoto place={place}/>
    {photo && <><button type="button" aria-label="장소 사진 확대" onClick={() => dialog.current?.showModal()} className="absolute right-3 bottom-3 flex min-h-11 items-center gap-2 rounded-full bg-white/95 px-4 text-xs font-semibold text-[#17172B]"><Expand size={16} aria-hidden="true"/>사진 보기</button>
      <dialog ref={dialog} aria-label="장소 사진 확대" onClick={event => { if (event.target === event.currentTarget) dialog.current?.close(); }} className="fixed m-auto w-[min(1100px,95vw)] max-w-none rounded-2xl border-0 bg-white p-3 backdrop:bg-[#17172B]/75">
        <div className="mb-2 flex items-center justify-between gap-3 px-2"><span className="text-sm font-semibold">{place.name}</span><button type="button" aria-label="확대 사진 닫기" onClick={() => dialog.current?.close()} className="grid size-11 place-items-center rounded-full hover:bg-slate-100"><X size={20}/></button></div>
        {/* Type3 photos are displayed without cropping or transformation. */}
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={photo.url} alt={`${place.name} 시설 사진 확대`} className="max-h-[75dvh] w-full object-contain" referrerPolicy="no-referrer"/>
        <PlacePhotoCredit place={place} className="px-2 pt-2 text-xs"/>
      </dialog></>}
  </div>;
}
