import type { Metadata } from 'next';
import { Suspense } from 'react';
import PlaceDetail from './_components/PlaceDetail/PlaceDetail';

export const metadata: Metadata = { title: '장소 상세 | OUTPICK' };

export default function PlaceDetailPage() {
  return (
    <Suspense fallback={<main className="p-8 text-slate-700"><p role="status">장소 정보를 준비하고 있어요.</p></main>}>
      <PlaceDetail />
    </Suspense>
  );
}
