.DEFAULT_GOAL := help

IMAGE_TAG ?= v0

.PHONY: \
	help \
	backend-docker-build \
	backend-docker-build-push \
	compose-up \
	compose-down \
	compose-reset \
	places-import \
	places-list \
	places-publish-initial \
	places-init \
	mysql-shell \
	frontend-dev \
	frontend-lint \
	frontend-typecheck \
	frontend-test \
	frontend-build \
	frontend-preview \
	frontend-docker-build \
	frontend-docker-build-push

help:
	@echo "-------------------------------"
	@echo ""
	@echo "       Outpick Makefile"
	@echo ""
	@echo "-------------------------------"
	@echo "make backend-docker-build         Build AMD64/ARM64 backend images locally (IMAGE_TAG=v0)"
	@echo "make backend-docker-build-push    Build AMD64/ARM64 backend images and push to Docker Hub (IMAGE_TAG=v0)"
	@echo "-------------------------------"
	@echo "make compose-up                   Start local MySQL and wait until ready"
	@echo "make compose-down                 Stop local MySQL (keep data)"
	@echo "make compose-reset                Delete local DB data and restart (confirmation required)"
	@echo "make places-import                Collect initial TourAPI places into local DB as drafts"
	@echo "make places-list                  Show imported places and publication status"
	@echo "make places-publish-initial       Publish reviewed initial places and local map test places"
	@echo "make places-init                  Start local DB, collect, publish and list (keep existing data)"
	@echo "make mysql-shell                  Open local MySQL shell"
	@echo "-------------------------------"
	@echo "make frontend-dev                 Install dependencies if needed and start frontend"
	@echo "make frontend-lint                Run frontend ESLint"
	@echo "make frontend-typecheck           Check frontend TypeScript types"
	@echo "make frontend-test                Run frontend tests once"
	@echo "make frontend-build               Build frontend static files"
	@echo "make frontend-preview             Serve existing frontend build on localhost:3000 (Python 3 required)"
	@echo "make frontend-docker-build        Build AMD64/ARM64 frontend images locally (IMAGE_TAG=v0)"
	@echo "make frontend-docker-build-push   Build AMD64/ARM64 frontend images and push to Docker Hub (IMAGE_TAG=v0)"
	@echo "-------------------------------"

# ---------------------------------------------------------------------------------
#
# backend
# - backend 개발 환경 커멘드
#
# ---------------------------------------------------------------------------------
# AMD64·ARM64 이미지를 빌드해 로컬에 저장합니다. containerd 이미지 저장소가 필요합니다.
backend-docker-build:
	docker buildx build --platform linux/amd64,linux/arm64 -t "chocobe/outpick-backend:$(IMAGE_TAG)" --load ./backend

# Mac과 EC2용 이미지를 함께 빌드해 업로드합니다. Docker Hub 로그인이 필요합니다.
backend-docker-build-push:
	docker buildx build --platform linux/amd64,linux/arm64 -t "chocobe/outpick-backend:$(IMAGE_TAG)" --push ./backend

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

# 기존 데이터를 유지하며 순서대로 실행합니다. 실패하면 다음 단계로 진행하지 않습니다.
places-init:
	$(MAKE) compose-up
	$(MAKE) places-import
	$(MAKE) places-publish-initial
	$(MAKE) places-list

places-import:
	node backend/scripts/local-places.mjs import

places-list:
	node backend/scripts/local-places.mjs list

places-publish-initial:
	node backend/scripts/local-places.mjs publish-initial

mysql-shell:
	node backend/scripts/local-places.mjs shell

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

# AMD64·ARM64 이미지를 빌드해 로컬에 저장합니다. containerd 이미지 저장소가 필요합니다.
frontend-docker-build:
	node frontend/scripts/docker-build.mjs --load "$(IMAGE_TAG)"

# Mac과 EC2용 이미지를 함께 빌드해 업로드합니다. Docker Hub 로그인이 필요합니다.
frontend-docker-build-push:
	node frontend/scripts/docker-build.mjs --push "$(IMAGE_TAG)"
