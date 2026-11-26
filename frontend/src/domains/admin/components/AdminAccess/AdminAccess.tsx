import type { ReactNode } from 'react';
import ProtectedPage from '@/domains/member/components/ProtectedPage';

/** 현재 회원이 운영자로 확인된 뒤에 관리자 화면을 표시합니다. */
export default function AdminAccess({ children }: { children: ReactNode }) {
  return (
    <ProtectedPage requiredRole="ADMIN" loadingMessage="운영자 권한을 확인하고 있어요.">
      {children}
    </ProtectedPage>
  );
}
