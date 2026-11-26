'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useId } from 'react';
import type { Member } from '@/domains/member/api/memberApi';
import { adminNavigationItems, commonNavigationItems, memberNavigationItems } from '@/domains/member/navigation/accountNavigationItems';
import NotificationUnreadIndicator from '@/domains/notification/components/NotificationUnreadIndicator';

const regularMemberItems = [...commonNavigationItems, ...memberNavigationItems];

function NavigationLinks({ items, pathname, memberId }: {
  items: typeof commonNavigationItems;
  pathname: string;
  memberId?: number;
}) {
  const unreadDescriptionId = useId();

  return (
    <ul className="flex flex-wrap gap-1 md:flex-col">
      {items.map(({ href, label, Icon }) => (
        <li key={href}>
          <Link
            href={href}
            aria-current={pathname === href.replace(/\/$/, '') ? 'page' : undefined}
            aria-describedby={href === '/mypage/notifications/' ? unreadDescriptionId : undefined}
            className="flex min-h-11 items-center gap-2 rounded-lg px-2.5 py-2 text-sm font-semibold whitespace-nowrap text-[#51627a]
                hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#5147ff]
                aria-[current=page]:bg-[#eaf5ff] aria-[current=page]:text-[#4338e8] md:px-3"
          >
            <Icon size={18} className="shrink-0" aria-hidden="true" />
            {label}
            {href === '/mypage/notifications/' && memberId !== undefined && (
              <NotificationUnreadIndicator memberId={memberId} descriptionId={unreadDescriptionId} />
            )}
          </Link>
        </li>
      ))}
    </ul>
  );
}

export default function AccountSidebar({ member }: { member: Pick<Member, 'id' | 'role'> }) {
  const pathname = usePathname().replace(/\/$/, '');
  const { id, role } = member;

  return (
    <aside className="rounded-2xl border border-[#e4e9f1] bg-white p-2.5 md:p-3">
      <nav aria-label="공통 메뉴">
        <NavigationLinks items={role === 'USER' ? regularMemberItems : commonNavigationItems} pathname={pathname} memberId={id} />
      </nav>
      {role === 'ADMIN' && (
        <nav aria-label="관리자 메뉴" className="mt-3 border-t border-[#e4e9f1] pt-2">
          <NavigationLinks items={adminNavigationItems} pathname={pathname} />
        </nav>
      )}
    </aside>
  );
}
