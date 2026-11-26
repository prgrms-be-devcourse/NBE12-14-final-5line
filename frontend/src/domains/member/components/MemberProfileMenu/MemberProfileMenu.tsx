'use client';

import { CircleUserRound } from 'lucide-react';
import Link from 'next/link';
import { useEffect, useId, useRef, useState } from 'react';
import NotificationMenu from '@/domains/notification/components/NotificationMenu';
import { useCurrentMember } from '../../hooks/useCurrentMember';
import { memberShortcutItems } from '../../navigation/accountNavigationItems';
import { profileMenuItems, type ProfileMenuAudience } from './profileMenuItems';

const profileLinkClassName = 'block hover:bg-[#f3f1ff] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#5147ff]';
const iconClassName = 'grid size-11 place-items-center rounded-full border border-transparent text-[#16243b] hover:bg-[#f3f1ff] hover:text-[#5147ff] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#5147ff]';

type MemberStatus = 'checking' | 'error' | ProfileMenuAudience;

/** 현재 회원의 로그인 상태와 역할에 맞춰 헤더의 프로필 링크를 표시합니다. */
export default function MemberProfileMenu() {
  const { data: member, isPending, isError } = useCurrentMember();
  const memberStatus: MemberStatus = isPending ? 'checking' : isError ? 'error'
    : member ? (member.role === 'ADMIN' ? 'admin' : member.role === 'USER' ? 'member' : 'account') : 'guest';
  const [isOpen, setIsOpen] = useState(false);
  const menuId = useId();
  const menuRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const visibleMenuItems = memberStatus === 'checking' || memberStatus === 'error'
    ? []
    : profileMenuItems[memberStatus];

  useEffect(() => {
    if (!isOpen) return;

    function closeOnOutsidePress(event: PointerEvent) {
      if (event.target instanceof Node && !menuRef.current?.contains(event.target)) setIsOpen(false);
    }

    document.addEventListener('pointerdown', closeOnOutsidePress);
    return () => document.removeEventListener('pointerdown', closeOnOutsidePress);
  }, [isOpen]);

  return (
    <>
      {memberStatus === 'member' && memberShortcutItems.map(({ href, label, Icon }) => (
        <Link key={href} href={href} aria-label={label} className={iconClassName}>
          <Icon size={22} strokeWidth={1.8} aria-hidden="true" />
        </Link>
      ))}
      {memberStatus === 'member' && member && <NotificationMenu key={member.id} memberId={member.id} iconClassName={iconClassName} />}
      <div
        className="relative"
        ref={menuRef}
        onKeyDown={event => {
          if (event.key !== 'Escape' || !isOpen) return;
          event.stopPropagation();
          setIsOpen(false);
          buttonRef.current?.focus();
        }}
      >
        <button
          type="button"
          className={`${iconClassName} cursor-pointer aria-expanded:bg-[#f3f1ff] aria-expanded:text-[#5147ff]`}
          ref={buttonRef}
          aria-label="프로필 메뉴"
          aria-expanded={isOpen}
          aria-controls={menuId}
          onClick={() => setIsOpen(open => !open)}
        >
          <CircleUserRound size={22} strokeWidth={1.8} aria-hidden="true" />
        </button>
        <div
          id={menuId}
          className="absolute top-full right-0 z-20 mt-2 w-[min(200px,calc(100vw_-_40px))]
            rounded-[14px] border border-[#e4e9f1] bg-white p-1.5 text-[14px] text-[#16243b]
            shadow-[0_12px_28px_rgb(22_36_59_/_12%)] *:rounded-lg *:px-3 *:py-2.5"
          hidden={!isOpen}
        >
          {memberStatus === 'checking' && <p role="status">계정 상태를 확인하고 있어요.</p>}
          {memberStatus === 'error' && <p role="alert">계정 정보를 확인하지 못했어요.</p>}
          {visibleMenuItems.map(({ href, label }) => (
            <Link key={href} href={href} className={profileLinkClassName} onClick={() => setIsOpen(false)}>
              {label}
            </Link>
          ))}
        </div>
      </div>
    </>
  );
}
