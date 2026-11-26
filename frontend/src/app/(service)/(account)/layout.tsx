'use client';

import type { ReactNode } from 'react';
import { useCurrentMember } from '@/domains/member/hooks/useCurrentMember';
import AccountSidebar from './_components/AccountSidebar/AccountSidebar';

export default function AccountLayout({ children }: { children: ReactNode }) {
  const { data: member, isError } = useCurrentMember();
  const showSidebar = member && !isError;

  return (
    <div className={showSidebar
      ? 'mx-auto grid max-w-7xl items-start gap-6 px-4 py-6 md:grid-cols-[12rem_minmax(0,1fr)] md:px-6 lg:gap-8 lg:px-9 lg:py-9'
      : undefined}
    >
      {showSidebar && <AccountSidebar member={member} />}
      <main className="min-w-0">{children}</main>
    </div>
  );
}
