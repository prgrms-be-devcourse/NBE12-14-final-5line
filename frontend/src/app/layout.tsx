import type { Metadata } from 'next';
import localFont from 'next/font/local';
import QueryProvider from './_providers/QueryProvider';
import './globals.css';

const pretendard = localFont({
  src: './_fonts/PretendardVariable.woff2',
  weight: '45 920',
  display: 'swap',
  fallback: ['ui-sans-serif', 'system-ui', 'sans-serif'],
  adjustFontFallback: false,
});

export const metadata: Metadata = {
  title: 'OUTPICK',
  description: '스포츠·액티비티 장소 탐색 서비스',
};

/** 모든 페이지에 한국어 문서 설정·공통 스타일과 서버 상태 조회용 Provider를 적용합니다. */
export default function RootLayout({ children }: LayoutProps<'/'>) {
  return (
    <html lang="ko" className={pretendard.className}>
      <body className="mx-auto max-w-[1440px]">
        <QueryProvider>{children}</QueryProvider>
      </body>
    </html>
  );
}
