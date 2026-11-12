import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  // Next.js 실행 서버 없이 배포할 정적 파일을 out 디렉터리에 생성합니다.
  output: 'export',
  // 경로별 디렉터리에 index.html을 만들어 정적 서버에서 직접 접속과 새로고침을 지원합니다.
  trailingSlash: true,
  // 개발 서버가 별도의 AGENTS.md를 자동 생성하지 않도록 합니다.
  agentRules: false,
  turbopack: {
    rules: {
      '*.css': {
        loaders: ['@tailwindcss/turbopack'],
        as: '*.css',
      },
    },
  },
};

export default nextConfig;
