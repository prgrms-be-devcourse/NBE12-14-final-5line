export default function PlaceCardSkeleton() {
  return <div role="status" aria-label="장소 정보 로딩 중" className="mt-4"><span className="sr-only">장소 정보를 불러오고 있어요.</span>
    <div aria-hidden="true" className="outpick-placeGrid grid gap-x-5 gap-y-7">{Array.from({length:6}, (_, index) => <div key={index} className="overflow-hidden rounded-[13px] motion-safe:animate-pulse">
      <div className="aspect-[4/3] bg-[#eef0f4]"/>
      <div className="space-y-2 p-3"><div className="h-3 w-2/3 rounded bg-[#eef0f4]"/><div className="h-4 w-5/6 rounded bg-[#eef0f4]"/><div className="h-4 w-1/2 rounded bg-[#eef0f4]"/></div>

    </div>)}</div>
  </div>;
}
