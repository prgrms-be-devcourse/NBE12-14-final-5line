import { apiClient } from '@/shared/lib/http/apiClient';
import type { ExamplePreviewRequest, ExampleResponse } from '../types/example.type';

/** 예시를 조회하고 응답 DTO만 반환합니다. signal은 화면 이동 등의 요청 취소에 사용합니다. */
export async function getExample({ id, signal }: { id: number; signal?: AbortSignal }): Promise<ExampleResponse> {
  const response = await apiClient.get<ExampleResponse>(
    `/api/v1/examples/${id}`,
    { signal }
  );

  return response.data;
}

/** 입력값을 Backend에서 검증한 뒤 미리보기를 반환합니다. 데이터를 저장하지 않습니다. */
export async function previewExample(input: ExamplePreviewRequest): Promise<ExampleResponse> {
  const response = await apiClient.post<ExampleResponse>(
    '/api/v1/examples/preview',
    input
  );

  return response.data;
}
