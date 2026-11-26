import type { ReactNode } from 'react';
import ProtectedPage from '@/domains/member/components/ProtectedPage';

export default function MypageLayout({ children }: { children: ReactNode }) {
  return <ProtectedPage>{children}</ProtectedPage>;
}
