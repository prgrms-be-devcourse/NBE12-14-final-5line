import axios, { type InternalAxiosRequestConfig } from 'axios';
import { toApiError } from './apiError';

const baseURL = process.env.NEXT_PUBLIC_API_BASE_URL;
if (!baseURL) {
  throw new Error('NEXT_PUBLIC_API_BASE_URL을 설정해 주세요. frontend/.env.example을 참고하세요.');
}

// 배포에서는 /로 현재 화면의 서버에 요청하고, 로컬 개발에서는 별도 Backend 주소를 사용합니다.
if (baseURL !== '/') {
  const apiUrl = new URL(baseURL);
  if (!['http:', 'https:'].includes(apiUrl.protocol)) {
    throw new Error('NEXT_PUBLIC_API_BASE_URL은 / 또는 http·https 주소여야 합니다.');
  }
}

/** 도메인 API 함수가 공유하는 클라이언트입니다. */
export const apiClient = axios.create({
  baseURL,
  withCredentials: true,
  timeout: 10_000,
});

type RetryConfig = InternalAxiosRequestConfig & { _authRetried?: boolean };
let refreshInFlight: Promise<void> | null = null;
const authBase = baseURL === '/' ? '' : baseURL.replace(/\/$/, '');

async function refreshAccess(): Promise<void> {
  const { data } = await axios.get<{ token: string; headerName: string }>(`${authBase}/api/v1/auth/csrf`, {
    withCredentials: true,
    timeout: 10_000,
  });
  await axios.post(`${authBase}/api/v1/auth/refresh`, undefined, {
    withCredentials: true,
    timeout: 10_000,
    headers: { [data.headerName]: data.token },
  });
}

apiClient.interceptors.response.use(
  response => response,
  async (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 401 && error.config) {
      const config = error.config as RetryConfig;
      const path = config.url ?? '';
      if (!config._authRetried && !path.includes('/auth/')) {
        config._authRetried = true;
        refreshInFlight ??= refreshAccess().finally(() => { refreshInFlight = null; });
        try {
          await refreshInFlight;
        } catch {
          return Promise.reject(toApiError(error));
        }
        return apiClient.request(config);
      }
    }
    return Promise.reject(toApiError(error));
  }
);
