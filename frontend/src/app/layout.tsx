import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "OUTPICK",
  description: "스포츠·액티비티 장소 탐색 서비스",
};

/** 모든 페이지에 한국어 문서 설정과 공통 스타일을 적용합니다. */
export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
