'use client';
import { useRef, useState } from 'react';
import { SlidersHorizontal, X } from 'lucide-react';
type Filters = { region: string; season: string; environment: string };
export default function MobilePlaceFilters({ filters, regions, seasons, onApply }: { filters: Filters; regions: {id:number;name:string}[]; seasons: string[]; onApply: (filters: Filters) => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [draft, setDraft] = useState(filters);
  const selected = Object.entries(filters).filter(([,value])=>Boolean(value)) as [keyof Filters,string][];
  function open() { setDraft(filters); dialog.current?.showModal(); }
  function close() { dialog.current?.close(); }
  const selectClass = 'mt-2 w-full rounded-xl border border-[#dfe4ed] bg-white p-3 text-sm text-[#17172B] focus-visible:outline-2 focus-visible:outline-[#5942FF]';
  return <div className="w-full py-3 md:hidden">
    <div className="flex flex-wrap items-center gap-2"><button type="button" onClick={open} aria-haspopup="dialog" className="inline-flex min-h-11 items-center gap-2 rounded-xl border border-[#ddd9ff] px-3 text-sm font-semibold text-[#5942FF]"><SlidersHorizontal size={16} aria-hidden="true"/>필터{selected.length>0 && <span className="rounded-full bg-[#F3F0FF] px-2 text-xs">{selected.length}</span>}</button>
      {selected.map(([key,value])=><button key={key} type="button" aria-label={`${value} 필터 해제`} onClick={()=>onApply({...filters,[key]:''})} className="inline-flex min-h-9 max-w-full items-center gap-1 rounded-full bg-[#F3F0FF] px-3 text-xs text-[#5942FF]"><span className="truncate">{value}</span><X size={12} aria-hidden="true"/></button>)}
    </div>
    <dialog ref={dialog} aria-labelledby="mobile-filters-title" onClick={event=>{if(event.target===event.currentTarget)close();}} className="fixed inset-x-0 top-auto bottom-0 m-0 max-h-[85dvh] w-full max-w-none overflow-y-auto rounded-t-2xl border-0 bg-white p-5 pb-[calc(env(safe-area-inset-bottom)+20px)] text-[#17172B] backdrop:bg-[#17172B]/35">
      <div className="flex items-center justify-between"><h2 id="mobile-filters-title" className="text-lg font-bold">탐색 필터</h2><button type="button" onClick={close} aria-label="필터 닫기" className="flex size-11 items-center justify-center rounded-lg hover:bg-[#F5F6F8]"><X size={20} aria-hidden="true"/></button></div>
      <div className="mt-5 space-y-5">
        <label className="block text-sm font-semibold">지역<select className={selectClass} value={draft.region} onChange={event=>setDraft({...draft,region:event.target.value})}><option value="">지역 전체</option>{draft.region&&!regions.some(region=>region.name===draft.region)&&<option value={draft.region}>{draft.region}</option>}{regions.map(region=><option key={region.id} value={region.name}>{region.name}</option>)}</select></label>
        <label className="block text-sm font-semibold">시즌<select className={selectClass} value={draft.season} onChange={event=>setDraft({...draft,season:event.target.value})}><option value="">시즌 전체</option>{seasons.map(season=><option key={season}>{season}</option>)}</select></label>
        <label className="block text-sm font-semibold">실내외<select className={selectClass} value={draft.environment} onChange={event=>setDraft({...draft,environment:event.target.value})}><option value="">실내외 전체</option><option>실내</option><option>실외</option></select></label>
      </div>
      <div className="mt-6 flex gap-3"><button type="button" onClick={()=>setDraft({region:'',season:'',environment:''})} className="min-h-11 rounded-xl border border-[#dfe4ed] px-4 text-sm">초기화</button><button type="button" onClick={()=>{onApply(draft);close();}} className="min-h-11 flex-1 rounded-xl bg-[#5942FF] px-4 text-sm font-semibold text-white">필터 적용</button></div>
    </dialog>
  </div>;
}
