#!/usr/bin/env bash
set -euo pipefail

# EC2 배포 디렉터리를 기준으로 실행하며, .env를 셸 코드로 실행하지 않습니다.
cd "$(dirname "${BASH_SOURCE[0]}")/.."
if [[ $# -ne 0 ]]; then
    echo "사용법: bash scripts/init-places.sh" >&2
    exit 1
fi

publication_sql=backend/scripts/review-initial-tour-places.sql
for path in .env compose.deploy.yaml compose.places.yaml "$publication_sql"; do
    [[ -f "$path" ]] || { echo "필요한 파일이 없습니다: $path" >&2; exit 1; }
done

# 부모 터미널에 남은 키 대신 배포 디렉터리 .env의 키를 사용합니다.
unset TOUR_API_KEY
compose=(docker compose --env-file .env -f compose.deploy.yaml)
import_compose=("${compose[@]}" -f compose.places.yaml)
"${import_compose[@]}" config --quiet

# 비밀번호는 MySQL 컨테이너 안에서 읽고, 명령 인자에 값을 직접 넣지 않습니다.
mysql_command='MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --default-character-set=utf8mb4'

echo "1/4 배포 DB 접속 확인"
"${compose[@]}" exec -T mysql sh -c "$mysql_command" <<'SQL'
SELECT 1 AS db_connection_ok;
SQL

echo "2/4 초기 장소 수집"
"${import_compose[@]}" run -T --rm --no-deps --entrypoint java backend \
    -Dloader.main=com.outpick.domain.place.importer.TourApiImportCommand \
    -cp /app/app.jar \
    org.springframework.boot.loader.launch.PropertiesLauncher

echo "3/4 검수된 초기 장소와 지도 검증용 장소 공개"
"${compose[@]}" exec -T mysql sh -c "$mysql_command" < "$publication_sql"

echo "4/4 수집한 장소와 공개 상태 조회"
"${compose[@]}" exec -T mysql sh -c "$mysql_command" <<'SQL'
SELECT p.place_id, p.name, p.address, p.homepage, p.publish_status, i.content_id
FROM tour_api_import i
JOIN place p ON p.place_id = i.place_id
ORDER BY p.place_id;
SQL

echo "처리 완료. publish_status가 1인 장소를 브라우저에서 확인하세요."
