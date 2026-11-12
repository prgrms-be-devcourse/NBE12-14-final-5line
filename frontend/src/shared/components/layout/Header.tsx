import Link from 'next/link';

/** 일반·관리자 레이아웃에서 제목과 예시 진입 화면 링크를 표시합니다. */
export default function Header({ title }: { title: string }) {
  return (
    <header className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-200 bg-white px-6 py-4">
      <p className="text-xl font-bold">{title}</p>
      <Link className="rounded px-2 py-1 text-blue-700 underline" href="/">
        예시 목록
      </Link>
    </header>
  );
}
