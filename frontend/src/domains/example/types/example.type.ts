/** Backend ExampleCreateRequest와 대응하는 미리보기 입력입니다. 저장 요청은 아닙니다. */
export type ExamplePreviewRequest = {
  name: string;
  participantCount: number;
};

/** Backend ExampleResponse와 대응하며 data 래퍼를 추가하지 않습니다. */
export type ExampleResponse = {
  name: string;
  participantCount: number;
};
