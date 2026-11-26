import { Suspense } from 'react';
import PlaceExplorer from './_components/PlaceExplorer/PlaceExplorer';

export default function Home() {
  return (
    <Suspense fallback={<main className="p-8 text-slate-700"><p role="status">장소 탐색 화면을 준비하고 있어요.</p></main>}>
      <PlaceExplorer />
    </Suspense>
  );
}
