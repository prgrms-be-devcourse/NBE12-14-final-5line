.DEFAULT_GOAL := help

.PHONY:
	help \
	compose-up \
	compose-down \
	compose-reset \
	frontend-dev \
	frontend-lint \
	frontend-typecheck \
	frontend-test \
	frontend-build \
	frontend-preview

help:
	@echo "make compose-up     Start local MySQL and wait until ready"
	@echo "make compose-down   Stop local MySQL (keep data)"
	@echo "make compose-reset  Delete local DB data and restart (confirmation required)"
	@echo "make frontend-dev        Install dependencies if needed and start frontend"
	@echo "make frontend-lint       Run frontend ESLint"
	@echo "make frontend-typecheck  Check frontend TypeScript types"
	@echo "make frontend-test       Run frontend tests once"
	@echo "make frontend-build      Build frontend static files"
	@echo "make frontend-preview    Serve existing frontend build on localhost:3000 (Python 3 required)"

# ---------------------------------------------------------------------------------
#
# compose
# - 개발 환경 구성 커멘드
#
# ---------------------------------------------------------------------------------

# 개발 환경 구성 실행하기
compose-up:
	docker compose up -d --wait --wait-timeout 180

# 개발 환경 구성 종료하기
compose-down:
	docker compose down

# 개발 환경 구성 초기화하기
compose-reset:
	@printf 'Delete all local MySQL data? Type RESET: '; \
	read -r answer; \
	if [ "$$answer" != "RESET" ]; then echo "Cancelled."; exit 1; fi; \
	docker compose down --volumes && $(MAKE) compose-up

# ---------------------------------------------------------------------------------
#
# frontend
# - frontend 개발 환경 커멘드
#
# ---------------------------------------------------------------------------------

# 필요한 경우에만 의존성을 설치한 뒤 현재 터미널에서 개발 서버를 실행합니다.
frontend-dev:
	node frontend/scripts/dev.mjs

frontend-lint:
	npm --prefix frontend run lint

frontend-typecheck:
	npm --prefix frontend run typecheck

frontend-test:
	npm --prefix frontend run test

frontend-build:
	npm --prefix frontend run build

# 기존 빌드 파일만 제공합니다. 소스 변경 후에는 frontend-build를 먼저 실행합니다.
frontend-preview:
	@test -f frontend/out/index.html || { echo "빌드 결과가 없습니다. make frontend-build를 먼저 실행하세요."; exit 1; }
	python3 -m http.server 3000 --bind 127.0.0.1 --directory frontend/out
