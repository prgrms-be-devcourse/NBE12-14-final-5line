import axios from 'axios';
import type { ApiFailure, FieldError } from './apiError.type';

/** HTTP 응답 오류와 응답 없는 실패를 담습니다. details.kind로 구분하면 HTTP 정보에 안전하게 접근할 수 있습니다. */
export class ApiError extends Error {
  /** 실패 종류별 정보와 원래 예외를 보관하고, 공통 설명을 Error 메시지로 제공합니다. */
  constructor(public readonly details: ApiFailure, cause: unknown) {
    super(details.message, { cause });
    this.name = 'ApiError';
  }
}

/** 외부 JSON이 필드 오류 형식인지 검사합니다. 서버가 반환한 입력값 자체는 보관하지 않습니다. */
function isFieldError(value: unknown): value is FieldError {
  return typeof value === 'object' && value !== null
    && 'field' in value && typeof value.field === 'string'
    && 'message' in value && typeof value.message === 'string';
}

/** Axios 실패를 공통 오류로 변환합니다. HTTP 통신과 무관한 예외는 원래 오류를 유지합니다. */
export function toApiError(error: unknown): unknown {
  if (!axios.isAxiosError<unknown>(error)) {
    return error;
  }

  if (axios.isCancel(error)) {
    return new ApiError({ kind: 'canceled', message: '요청이 취소되었습니다.' }, error);
  }

  if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
    return new ApiError({ kind: 'timeout', message: '요청 시간이 초과되었습니다.' }, error);
  }

  if (!error.response) {
    return new ApiError({ kind: 'network', message: '서버에 연결할 수 없습니다.' }, error);
  }

  const { 
    status, 
    data, 
    headers,
  } = error.response;

  const contentType: unknown = headers['content-type'];
  const isProblemDetail = typeof contentType === 'string'
    && contentType.split(';')[0].trim().toLowerCase() === 'application/problem+json';

  const body = isProblemDetail && typeof data === 'object' && data !== null
    ? data 
    : {};

  const detail = 'detail' in body && typeof body.detail === 'string' 
    ? body.detail 
    : undefined;

  const title = 'title' in body && typeof body.title === 'string' 
    ? body.title 
    : undefined;

  const code = 'code' in body && typeof body.code === 'string' 
    ? body.code 
    : undefined;

  const requestId = 'requestId' in body && typeof body.requestId === 'string' 
    ? body.requestId 
    : undefined;

  const fieldErrors = 'fieldErrors' in body && Array.isArray(body.fieldErrors)
    ? body.fieldErrors.filter(isFieldError)
    : [];

  return new ApiError({
    kind: 'http',
    message: detail || title || '요청을 처리할 수 없습니다.',
    status,
    code,
    requestId,
    fieldErrors,
  }, error);
}
