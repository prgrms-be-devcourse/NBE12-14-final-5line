import type { ReactNode } from 'react';
import MainLayout from '@/shared/components/layout/MainLayout';

/** 이 Route Group의 모든 페이지에 일반 레이아웃을 적용합니다. */
export default function MainRouteLayout({ children }: { children: ReactNode }) {
  return <MainLayout>{children}</MainLayout>;
}
