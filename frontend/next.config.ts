import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Next.js 실행 서버 없이 배포할 정적 파일을 out 디렉터리에 생성합니다.
  output: "export",
  // 개발 서버가 별도의 AGENTS.md를 자동 생성하지 않도록 합니다.
  agentRules: false,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
