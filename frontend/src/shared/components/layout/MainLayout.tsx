import type { ReactNode } from 'react';
import Header from './Header';
import Sidebar from './Sidebar';

/** 헤더와 일반 사이드바 옆에 페이지 본문을 배치합니다. */
export default function MainLayout({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <Header title="OUTPICK" />
      <div className="flex flex-col md:min-h-[calc(100vh-4rem)] md:flex-row">
        <Sidebar />
        <main className="min-w-0 flex-1 p-6 md:p-10">{children}</main>
      </div>
    </div>
  );
}
