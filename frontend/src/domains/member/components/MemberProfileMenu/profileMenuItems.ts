import { adminNavigationItems, commonNavigationItems, memberReportLink } from '../../navigation/accountNavigationItems';

export type ProfileMenuAudience = 'guest' | 'account' | 'member' | 'admin';

type ProfileMenuLink = {
  label: string;
  href: string;
};

export const profileMenuItems: Record<ProfileMenuAudience, readonly ProfileMenuLink[]> = {
  guest: [
    { label: '로그인', href: '/login/' },
    { label: '회원가입', href: '/signup/' },
  ],
  account: commonNavigationItems,
  member: [...commonNavigationItems, memberReportLink],
  admin: [...commonNavigationItems, ...adminNavigationItems],
};
