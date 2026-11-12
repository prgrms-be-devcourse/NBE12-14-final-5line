import type { ReactNode } from 'react';
import Header from './Header';
import AdminSidebar from './AdminSidebar';

/** 헤더와 관리자 사이드바 옆에 페이지 본문을 배치합니다. 접근 권한 검사는 담당하지 않습니다. */
export default function AdminLayout({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <Header title="OUTPICK 관리자" />
      <div className="flex flex-col md:min-h-[calc(100vh-4rem)] md:flex-row">
        <AdminSidebar />
        <main className="min-w-0 flex-1 p-6 md:p-10">{children}</main>
      </div>
    </div>
  );
}
