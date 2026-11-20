# 장소 후기 API와 사진 정책

후기는 장소별로 묶는다. 기능 정의서는 장소 또는 장소별 종목을 후기 대상으로 허용하지만, 이번 구현은 장소 단위 후기만 제공한다. 따라서 `review.place_id`를 사용하며 ERD v3의 `place_activity_connection_id`와 다르다. 종목별 후기가 필요해지면 별도 마이그레이션과 API 변경을 논의한다.

## 공개·권한

- `GET /api/v1/places/{placeId}/reviews`: 비회원도 공개 장소의 공개 후기만 조회한다. 다른 장소의 후기는 포함하지 않는다.
- `POST /api/v1/places/{placeId}/reviews`: 로그인 회원이 본문 1~2,000자의 후기를 작성한다. 별점·방문일은 아직 필수 정책이 없어 제공하지 않는다.
- `PUT`, `DELETE /api/v1/reviews/{reviewId}`: 작성자만 수정·삭제한다.
- `POST /api/v1/reviews/{reviewId}/reports`: 로그인 회원이 사유를 입력해 한 후기를 한 번 신고한다.
- `GET /api/v1/admin/review-reports`: 운영자만 대기 신고를 본다.
- `PATCH /api/v1/admin/review-reports/{reportId}/dismiss`: 운영자가 신고를 반려하고 후기는 공개 상태로 둔다.
- `PATCH /api/v1/admin/reviews/{reviewId}/hide`: 운영자가 후기를 숨기고 대기 신고를 승인 처리한다. 숨긴 후기와 사진은 공개 조회에서 제외된다.

## 사진

- 작성자만 기존 후기에 JPEG 또는 PNG 사진을 추가·삭제할 수 있다. 파일당 최대 5MiB, 후기당 최대 3장, 가로·세로 각각 6,000px 이하이며 이미지 전체 픽셀은 3,600만 이하로 제한한다.
- `POST /api/v1/reviews/{reviewId}/photos`는 `multipart/form-data`의 `file`을 받는다. `GET /api/v1/review-photos/{assetId}`는 공개 후기와 공개 장소에 연결된 사진만 제공한다. `DELETE /api/v1/reviews/{reviewId}/photos/{assetId}`는 작성자만 사용할 수 있다.
- ERD의 `media_asset`, `review_image`, `review_media`를 사용한다. 운영용 S3 저장소가 정해지지 않아 초기 구현은 `review_image_data`에 이미지 바이트를 저장한다. `storage_key`는 향후 외부 저장소 이전에 사용할 불변 ID다. 이 선택은 DB 용량을 늘리므로 운영 저장소가 확정되면 이전해야 한다.
- 후기·회원 삭제 시 사진 메타데이터와 바이트는 FK와 같은 DB 트랜잭션으로 삭제된다. 신고는 후기 삭제 시 함께 삭제된다.

상태 변경 API는 인증 쿠키와 CSRF 검증을 거친다. 운영자 권한은 서버에서 현재 회원의 `role`을 다시 조회해 확인한다.
