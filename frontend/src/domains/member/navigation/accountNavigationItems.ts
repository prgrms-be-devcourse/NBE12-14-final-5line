import { Bell, Bookmark, CircleUserRound, ClipboardCheck, Flag, LayoutDashboard, MapPin, Megaphone, MessageSquareWarning } from 'lucide-react';

export const commonNavigationItems = [
  { label: '마이페이지', href: '/mypage/', Icon: CircleUserRound },
];

export const memberReportLink = { label: '내 제보 상태', href: '/mypage/place-reports/', Icon: Flag };

export const memberShortcutItems = [
  { label: '관심 장소', href: '/mypage/favorites/', Icon: Bookmark },
];

export const memberNavigationItems = [
  memberReportLink,
  ...memberShortcutItems,
  { label: '알림함', href: '/mypage/notifications/', Icon: Bell },
];

export const adminNavigationItems = [
  { label: '운영 현황', href: '/admin/', Icon: LayoutDashboard },
  { label: '장소 관리', href: '/admin/places/', Icon: MapPin },
  { label: '운영 정보 검수', href: '/admin/operating-changes/', Icon: ClipboardCheck },
  { label: '공지 관리', href: '/admin/notices/', Icon: Megaphone },
  { label: '오류 제보', href: '/admin/place-reports/', Icon: Flag },
  { label: '후기 신고', href: '/admin/review-reports/', Icon: MessageSquareWarning },
];
