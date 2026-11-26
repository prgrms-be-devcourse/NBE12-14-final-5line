import type { ReactNode } from 'react';
import AdminAccess from '@/domains/admin/components/AdminAccess/AdminAccess';

export default function AdminRouteLayout({ children }: { children: ReactNode }) {
  return <AdminAccess>{children}</AdminAccess>;
}
