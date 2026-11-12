import Link from 'next/link';

/** 관리자 메뉴를 표시합니다. 링크는 실제 기능 대신 레이아웃 예시로 연결합니다. */
export default function AdminSidebar() {
  return (
    <aside className="border-b border-slate-200 bg-slate-100 p-4 md:w-56 md:shrink-0 md:border-r md:border-b-0">
      <nav aria-label="관리자 메뉴" className="space-y-2">
        <p className="px-3 font-semibold">관리자 메뉴</p>
        <Link className="block rounded px-3 py-2 text-blue-700 underline" href="/admin-example">
          관리자 화면
        </Link>
        <Link className="block rounded px-3 py-2 text-blue-700 underline" href="/main-example">
          일반 화면
        </Link>
      </nav>
    </aside>
  );
}
