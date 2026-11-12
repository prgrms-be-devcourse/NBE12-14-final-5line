import type { ReactNode } from 'react';

/** 이 그룹의 페이지에는 헤더나 사이드바를 추가하지 않고 본문만 전달합니다. */
export default function MinimalRouteLayout({ children }: { children: ReactNode }) {
  return children;
}
