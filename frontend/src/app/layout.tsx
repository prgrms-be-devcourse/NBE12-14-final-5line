import type { Metadata } from 'next';
import QueryProvider from './_providers/QueryProvider';
import './globals.css';

export const metadata: Metadata = {
  title: 'OUTPICK',
  description: '스포츠·액티비티 장소 탐색 서비스',
};

/** 모든 페이지에 한국어 문서 설정·공통 스타일과 서버 상태 조회용 Provider를 적용합니다. */
export default function RootLayout({ children }: LayoutProps<'/'>) {
  return (
    <html lang="ko">
      <body>
        <QueryProvider>{children}</QueryProvider>
      </body>
    </html>
  );
}
