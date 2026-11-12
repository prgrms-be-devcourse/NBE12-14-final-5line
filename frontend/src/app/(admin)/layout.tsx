import type { ReactNode } from 'react';
import AdminLayout from '@/shared/components/layout/AdminLayout';

/** 이 Route Group의 모든 페이지에 관리자 레이아웃을 적용합니다. */
export default function AdminRouteLayout({ children }: { children: ReactNode }) {
  return <AdminLayout>{children}</AdminLayout>;
}
