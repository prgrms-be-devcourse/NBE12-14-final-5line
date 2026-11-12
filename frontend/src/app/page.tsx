import Link from 'next/link';

/** 레이아웃별 예시로 이동하는 진입 화면입니다. 이 페이지에는 그룹 레이아웃을 적용하지 않습니다. */
export default function Home() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-50 p-6 text-slate-900">
      <section className="w-full max-w-2xl rounded-2xl border border-slate-200 bg-white p-8">
        <h1 className="text-3xl font-bold">OUTPICK 레이아웃 예시</h1>
        <p className="mt-4 text-slate-600">페이지별 레이아웃 구성을 확인할 수 있습니다.</p>
        <nav aria-label="레이아웃 예시" className="mt-6">
          <ul className="space-y-4">
            <li><Link className="text-blue-700 underline" href="/minimal-example">최소 레이아웃 — 본문만</Link></li>
            <li><Link className="text-blue-700 underline" href="/main-example">일반 레이아웃 — 헤더와 사이드바</Link></li>
            <li><Link className="text-blue-700 underline" href="/admin-example">관리자 레이아웃 — 관리자 메뉴</Link></li>
          </ul>
        </nav>
      </section>
    </main>
  );
}
