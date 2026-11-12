.DEFAULT_GOAL := help

.PHONY:
	help \
	dev-up \
	dev-down \
	dev-reset

help:
	@echo "make dev-up     Start local MySQL and wait until ready"
	@echo "make dev-down   Stop local MySQL (keep data)"
	@echo "make dev-reset  Delete local DB data and restart (confirmation required)"

# ---------------------------------------------------------------------------------
#
# dev
# - 개발 환경 구성 커멘드
#
# ---------------------------------------------------------------------------------

# 개발 환경 구성 실행하기
dev-up:
	docker compose up -d --wait --wait-timeout 180

# 개발 환경 구성 종료하기
dev-down:
	docker compose down

# 개발 환경 구성 초기화하기
dev-reset:
	@printf 'Delete all local MySQL data? Type RESET: '; \
	read -r answer; \
	if [ "$$answer" != "RESET" ]; then echo "Cancelled."; exit 1; fi; \
	docker compose down --volumes && $(MAKE) dev-up
