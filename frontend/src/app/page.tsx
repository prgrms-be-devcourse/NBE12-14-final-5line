/** 초기 실행과 Tailwind CSS 적용을 확인하기 위한 기본 페이지입니다. */
export default function Home() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-50 px-6 text-slate-900">
      <section className="max-w-xl rounded-2xl border border-slate-200 bg-white p-8">
        <h1 className="text-3xl font-bold">OUTPICK</h1>
        <p className="mt-4 text-slate-600">
          스포츠·액티비티 장소 탐색 서비스
        </p>
        <p className="mt-2 text-sm text-slate-500">
          Next.js · TypeScript · Tailwind CSS 개발 환경이 준비되었습니다.
        </p>
      </section>
    </main>
  );
}
