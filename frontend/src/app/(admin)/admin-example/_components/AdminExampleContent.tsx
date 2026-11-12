import Link from 'next/link';

/** 관리자 레이아웃 예시의 페이지 전용 본문입니다. 공통 레이아웃과 페이지의 역할을 구분해서 보여 줍니다. */
export default function AdminExampleContent() {
  return (
    <section className="max-w-3xl rounded-2xl border border-slate-200 bg-white p-6 md:p-8">
      <h1 className="text-3xl font-bold">관리자 레이아웃 예시</h1>
      <p className="mt-4 text-slate-700">관리자 헤더와 사이드바가 페이지 본문을 감쌉니다.</p>
      <p className="mt-3 text-slate-600">관리자 화면 배치 예시이며 로그인·관리자 권한 검사는 구현하지 않았습니다.</p>
      <Link className="mt-6 inline-block rounded px-2 py-1 text-blue-700 underline" href="/">
        예시 목록으로 돌아가기
      </Link>
    </section>
  );
}
