import type { ReactNode } from 'react';
import ProtectedPage from '@/domains/member/components/ProtectedPage';

/** 상위 마이페이지 레이아웃이 로그인 확인을 마친 뒤 일반 회원 전용 화면의 접근 권한을 확인합니다. */
export default function MemberOnlyLayout({ children }: { children: ReactNode }) {
  return <ProtectedPage requiredRole="USER">{children}</ProtectedPage>;
}
