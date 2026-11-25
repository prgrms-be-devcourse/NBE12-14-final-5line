'use client';

import { useCurrentMember } from '@/domains/member/hooks/useCurrentMember';
import NotificationList from '@/domains/notification/components/NotificationList';

export default function NotificationInbox() {
  const { data: member } = useCurrentMember();
  return member ? <NotificationList key={member.id} memberId={member.id} /> : null;
}
