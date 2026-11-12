.DEFAULT_GOAL := help

.PHONY:
	help \
	compose-up \
	compose-down \
	compose-reset \
	frontend-dev \
	frontend-lint \
	frontend-typecheck \
	frontend-build

help:
	@echo "make compose-up     Start local MySQL and wait until ready"
	@echo "make compose-down   Stop local MySQL (keep data)"
	@echo "make compose-reset  Delete local DB data and restart (confirmation required)"
	@echo "make frontend-dev        Install dependencies if needed and start frontend"
	@echo "make frontend-lint       Run frontend ESLint"
	@echo "make frontend-typecheck  Check frontend TypeScript types"
	@echo "make frontend-build      Build frontend static files"

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
	docker compose down --volumes && $(MAKE) dev-up

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

frontend-build:
	npm --prefix frontend run build
