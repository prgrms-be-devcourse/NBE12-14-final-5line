import axios from 'axios';
import { toApiError } from './apiError';

const baseURL = process.env.NEXT_PUBLIC_API_BASE_URL;
if (!baseURL) {
  throw new Error('NEXT_PUBLIC_API_BASE_URL을 설정해 주세요. frontend/.env.example을 참고하세요.');
}

const apiUrl = new URL(baseURL);
if (!['http:', 'https:'].includes(apiUrl.protocol)) {
  throw new Error('NEXT_PUBLIC_API_BASE_URL은 http 또는 https 주소여야 합니다.');
}

/** 도메인 API 함수가 공유하는 클라이언트입니다. 인증 갱신·재시도·화면 이동은 담당하지 않습니다. */
export const apiClient = axios.create({
  baseURL,
  withCredentials: true,
  timeout: 10_000,
});

// 성공 응답은 Axios 형태를 유지하고, 실패만 공통 오류로 변환합니다.
apiClient.interceptors.response.use(
  response => response,
  (error: unknown) => Promise.reject(toApiError(error))
);
