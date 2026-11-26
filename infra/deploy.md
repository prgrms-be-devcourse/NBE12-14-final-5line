# 새 EC2에 OUTPICK 배포하기

Terraform으로 EC2를 만든 뒤 이 문서를 순서대로 진행합니다. 완료하면 `https://outpick.chocobe.shop`에서 화면과 API를 사용하고 초기 장소 데이터를 확인할 수 있습니다. 기존 서버에 앱만 다시 배포한다면 [기존 EC2의 앱 업데이트](#기존-ec2의-앱-업데이트)로 이동합니다.

각 명령이 성공한 뒤 다음 단계로 진행합니다. 명령마다 로컬 Mac과 EC2 중 실행 위치를 표시합니다. 코드 블록의 `\`는 다음 줄과 이어지는 하나의 명령이라는 뜻이며, 뒤에 공백을 붙이지 않습니다.

## 시작 전 준비

- [Terraform 안내](README.md)에 따라 인프라를 생성합니다. SSH 22번은 내 IP, HTTP 80번과 HTTPS 443번은 외부 접속이 허용되어 있어야 합니다.
- Backend·Frontend의 최신 AMD64 이미지를 Docker Hub에 업로드합니다. 빌드·Push 명령은 [루트 README](../README.md#docker-이미지-빌드)에 있습니다. 현재 Compose의 태그는 둘 다 `v0`입니다.
- Gabia의 `outpick.chocobe.shop` A 레코드가 보관 중인 EIP를 가리키는지 확인합니다. 카카오맵의 JavaScript SDK 허용 도메인에도 서비스 주소를 등록합니다.
- SSH 개인키와 기존 TLS 인증서 백업을 준비합니다. 기존 인증서 백업이 있으면 재발급 대신 복원합니다.

EC2를 재생성하면 서버의 파일·Docker 볼륨·DB 데이터는 자동 복구되지 않습니다. 아래 장소 수집은 TourAPI 후보 500곳을 준비합니다. 기존 DB 전체를 복구하려면 별도의 백업이 필요합니다.

## 1. SSH 접속과 Docker 준비 확인

**로컬 Mac · 프로젝트 루트**에서 실행합니다. 개인키 경로를 바꾸고 이후 로컬 명령은 같은 터미널에서 실행합니다.

```bash
SSH_KEY='/개인키/절대경로/devcos-team05--ec2-keypair.pem'
EC2_IP="$(terraform -chdir=infra output -raw public_ip)"
TLS_BACKUP=''
chmod 400 "$SSH_KEY"
ssh -i "$SSH_KEY" "ubuntu@$EC2_IP"
```

`TLS_BACKUP`은 인증서 백업의 로컬 절대 경로를 넣는 변수입니다. 재생성한 서버라면 로컬 터미널에서 이 값을 기존 백업 경로로 바꿉니다. 백업이 없는 최초 발급일 때만 비워 둡니다. SSH 개인키와 TLS 백업은 서로 다른 파일입니다.

EC2 재생성 후 SSH 호스트 키 변경 경고가 나오면 AWS 콘솔에서 해당 EIP가 새 인스턴스에 연결되었는지 확인합니다. 확인 후 로컬에서 `ssh-keygen -R "$EC2_IP"`로 그 주소의 이전 지문만 제거하고 다시 접속합니다.

**EC2**에서 첫 부팅 초기화와 Docker 설치 완료를 기다립니다.

```bash
sudo cloud-init status --wait
```

`status: done`을 확인하고 `exit`으로 SSH를 종료합니다. **로컬**에서 같은 SSH 명령으로 다시 접속하면 새 Docker 그룹 권한이 적용됩니다.

```bash
ssh -i "$SSH_KEY" "ubuntu@$EC2_IP"
```

**EC2**에서 확인합니다.

```bash
docker --version
docker compose version
systemctl is-active docker
```

버전과 `active`가 나와야 합니다. 실패하면 `sudo tail -n 100 /var/log/cloud-init-output.log`와 `sudo journalctl -u docker --no-pager -n 100`으로 원인을 확인합니다.

## 2. 전체 배포 파일 업로드

**로컬 Mac · 프로젝트 루트**에서 실행합니다. SSH 세션과 별도의 로컬 터미널이라면 1번의 변수를 다시 설정합니다.

| 로컬 파일 | EC2의 `~/outpick/` 아래 위치 | 역할 |
| --- | --- | --- |
| `compose.deploy.yaml`, `compose.https.yaml`, `compose.places.yaml` | 같은 이름 | 기본 서비스·HTTPS·수집용 구성 |
| `.env.example` | `.env.example` | 서버 환경변수 작성 양식 |
| `nginx/default.conf`, `nginx/https.conf` | `nginx/` | HTTP·HTTPS 요청 처리 |
| `scripts/renew-certificates.sh`, `scripts/certificate-storage.sh`, `scripts/init-places.sh` | `scripts/` | 인증서 갱신·보관·초기 장소 준비 |
| `infra/systemd/outpick-certbot-renew.service`, `infra/systemd/outpick-certbot-renew.timer` | `infra/systemd/` | 인증서 자동 갱신 작업·예약 |
| `backend/scripts/review-initial-tour-places.sql` | `backend/scripts/` | 초기 검수 장소와 지도 검증용 장소 공개 |

```bash
ssh -i "$SSH_KEY" "ubuntu@$EC2_IP" \
  'mkdir -p ~/outpick/nginx ~/outpick/scripts ~/outpick/infra/systemd ~/outpick/backend/scripts'

scp -i "$SSH_KEY" compose.deploy.yaml compose.https.yaml compose.places.yaml .env.example \
  "ubuntu@$EC2_IP:~/outpick/"
scp -i "$SSH_KEY" nginx/default.conf nginx/https.conf \
  "ubuntu@$EC2_IP:~/outpick/nginx/"
scp -i "$SSH_KEY" scripts/renew-certificates.sh scripts/certificate-storage.sh scripts/init-places.sh \
  "ubuntu@$EC2_IP:~/outpick/scripts/"
scp -i "$SSH_KEY" infra/systemd/outpick-certbot-renew.service infra/systemd/outpick-certbot-renew.timer \
  "ubuntu@$EC2_IP:~/outpick/infra/systemd/"
scp -i "$SSH_KEY" backend/scripts/review-initial-tour-places.sql \
  "ubuntu@$EC2_IP:~/outpick/backend/scripts/"
```

`scp`는 파일을 복사합니다. 원격 목적지의 `:`를 생략하지 않습니다. Frontend 정적 파일과 Backend JAR는 이미지에 있으므로 따로 업로드하지 않습니다. 개인키와 로컬 `.env`도 위 명령으로 복사하지 않습니다.

인증서 백업이 있다면 **로컬**에서 `TLS_BACKUP`에 실제 경로를 설정한 뒤 업로드합니다.

```bash
scp -i "$SSH_KEY" "$TLS_BACKUP" "ubuntu@$EC2_IP:~/outpick-tls-restore.tar.gz"
```

백업이 없으면 이 명령을 건너뛰고 5-B에서 최초 발급합니다.

## 3. 서버 환경변수 작성

**EC2**에서 실행합니다.

```bash
cd ~/outpick
cp -n .env.example .env
chmod 600 .env
nano .env
```

`cp -n`은 기존 `.env`를 덮어쓰지 않습니다. `chmod 600`은 소유자만 읽고 쓰도록 설정합니다. 편집기에서 아래 값을 확인한 뒤 저장합니다. Nano는 `Ctrl+O`, Enter로 저장하고 `Ctrl+X`로 종료합니다.

- `MYSQL_DATABASE`, `MYSQL_USER`: 앱에서 사용할 DB와 사용자 이름
- `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`: 서버용 비밀번호. 서로 다른 값을 사용합니다.
- `TOUR_API_KEY`: TourAPI 일반 인증키(Decoding). 8번 장소 수집에 사용합니다.

키나 비밀번호를 명령 인자·문서·Git에 넣지 않습니다. Compose가 `.env`를 읽으므로 `source .env`나 `export`는 필요하지 않습니다. Frontend 카카오 JavaScript 키는 이미지 빌드 시 넣는 값이며 이 서버 `.env`에서 설정하지 않습니다.

기존 MySQL 볼륨이 있으면 `.env`의 비밀번호를 바꾸는 것만으로 DB 계정 비밀번호가 바뀌지는 않습니다.

## 4. 이미지 다운로드와 HTTP 실행

**EC2 · `~/outpick`**에서 실행합니다.

```bash
docker compose -f compose.deploy.yaml config --quiet
docker compose -f compose.deploy.yaml pull
docker compose -f compose.deploy.yaml up -d --wait
docker compose -f compose.deploy.yaml ps
curl --fail -i -w '\n' http://localhost:18080/actuator/health
```

`config --quiet`는 비밀번호를 포함한 최종 설정을 출력하지 않고 검사합니다. `pull`은 이미지를 다운로드하고, `up -d --wait`는 백그라운드에서 실행하며 서비스 상태를 기다립니다. Backend에는 상태 검사가 없으므로 health 응답의 `status: UP`까지 확인합니다.

앱 준비 전 요청이 실패하면 `docker compose -f compose.deploy.yaml logs --tail 100 backend`로 시작 로그와 오류를 확인하고 다시 요청합니다.

**로컬 브라우저**에서 `http://outpick.chocobe.shop`을 명시해 HTTP 접속을 확인합니다. DNS 확인이 필요하면 로컬에서 `dig +short outpick.chocobe.shop A`로 EIP와 비교합니다. 인증서가 없으므로 이 단계에서는 HTTPS 구성을 실행하지 않습니다.

## 5. 인증서 준비

기존 백업이 있으면 **5-A만**, 백업이 없는 최초 구성이라면 **5-B만** 진행합니다.

### 5-A. 기존 인증서 복원

2번에서 백업을 업로드한 뒤 **EC2**에서 실행합니다. 자신이 만든 신뢰할 수 있는 백업을 사용합니다.

```bash
bash scripts/certificate-storage.sh restore "$HOME/outpick-tls-restore.tar.gz"
docker compose -f compose.deploy.yaml run --rm --no-deps certbot certificates
docker compose -f compose.deploy.yaml run --rm --no-deps certbot renew \
  --cert-name outpick.chocobe.shop --non-interactive
```

복원은 비어 있는 인증서 볼륨에서만 가능합니다. 인증서 도메인과 만료일을 확인합니다. 마지막 명령은 갱신 시점이면 실제 갱신하고, 아직 유효하면 건너뜁니다. DNS와 HTTP가 동작해야 하며 갱신 명령은 내부 지연 때문에 몇 분 기다릴 수 있습니다. 오류 없이 끝나면 6번으로 이동합니다.

### 5-B. 백업이 없을 때 최초 발급

**EC2**에서 본인의 이메일을 지정합니다.

```bash
CERTBOT_EMAIL='본인 이메일 주소'
docker compose -f compose.deploy.yaml run --rm --no-deps certbot certonly \
  --webroot --webroot-path /var/www/certbot \
  --cert-name outpick.chocobe.shop -d outpick.chocobe.shop \
  --email "$CERTBOT_EMAIL" --agree-tos --non-interactive --dry-run
```

`certonly`는 인증서만 요청하고 Nginx 설정은 수정하지 않습니다. `--agree-tos`는 Let's Encrypt 이용약관 동의, `--dry-run`은 인증서를 저장하지 않는 테스트 발급입니다. `The dry run was successful`을 확인한 뒤 아래 실제 발급을 실행합니다.

```bash
docker compose -f compose.deploy.yaml run --rm --no-deps certbot certonly \
  --webroot --webroot-path /var/www/certbot \
  --cert-name outpick.chocobe.shop -d outpick.chocobe.shop \
  --email "$CERTBOT_EMAIL" --agree-tos --non-interactive
```

`Successfully received certificate`와 만료일이 나오면 6번으로 이동합니다. 백업이 있는 서버 재생성에서는 이 발급 절차를 반복하지 않습니다.

## 6. HTTPS 실행과 응답 확인

**EC2**에서 두 Compose 파일을 합쳐 실행합니다.

```bash
docker compose -f compose.deploy.yaml -f compose.https.yaml config --quiet
docker compose -f compose.deploy.yaml -f compose.https.yaml run --rm --no-deps nginx nginx -t
docker compose -f compose.deploy.yaml -f compose.https.yaml up -d --wait
```

`nginx -t`는 설정과 인증서 파일을 검사하며 서비스를 적용하는 명령은 아닙니다. `test is successful`을 확인한 뒤 `up`으로 HTTPS 설정을 적용합니다.

**로컬 Mac**에서 확인합니다.

```bash
curl -I http://outpick.chocobe.shop/
curl --fail -i -w '\n' https://outpick.chocobe.shop/api/v1/examples/1
```

첫 응답은 `308`과 HTTPS `Location`, 두 번째는 `200`과 JSON이어야 합니다. `-I`는 응답 헤더만, `-i`는 헤더와 본문을 보여줍니다. `--fail`은 HTTP 오류를 실패로 처리하고 `-w '\n'`은 출력 끝에 줄바꿈을 추가합니다.

브라우저에서 HTTPS 도메인의 화면·카카오맵·API 연결을 확인합니다. 인증서 경고가 없어야 합니다. 장소 데이터가 없는 것은 8번에서 처리합니다. HTTPS는 IP 대신 인증서의 도메인으로 확인합니다.

이후 서비스 실행·업데이트에는 두 Compose 파일을 함께 지정합니다. 기본 파일만으로 `up`하면 HTTP 구성으로 돌아갑니다.

## 7. 인증서 자동 갱신 등록

**EC2**에서 갱신 모의 테스트를 실행합니다. 아래 명령은 테스트 대기 시간을 생략하며 실제 인증서를 변경하지 않습니다.

```bash
docker compose -f compose.deploy.yaml run --rm --no-deps certbot renew \
  --cert-name outpick.chocobe.shop --non-interactive --dry-run --no-random-sleep-on-renew
```

`all simulated renewals succeeded`를 확인한 뒤 예약을 등록합니다.

```bash
sudo install -m 644 infra/systemd/outpick-certbot-renew.service /etc/systemd/system/
sudo install -m 644 infra/systemd/outpick-certbot-renew.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now outpick-certbot-renew.timer
systemctl list-timers outpick-certbot-renew.timer
```

`install`은 예약 파일을 시스템 경로에 복사합니다. `daemon-reload`는 변경된 파일을 읽고, `enable --now`는 지금부터 예약을 활성화하며 재부팅 후에도 유지합니다. `NEXT`에 다음 실행 시각이 나오는지 확인합니다.

등록한 서비스도 수동으로 한 번 실행해 확인합니다.

```bash
sudo systemctl start outpick-certbot-renew.service
sudo journalctl -u outpick-certbot-renew.service --no-pager -n 50
```

명령 완료 후 로그에서 갱신 성공 또는 갱신 시점이 아니라는 안내와 Nginx 설정 검사 성공을 확인합니다. 수동 실행에도 몇 분의 지연이 있을 수 있습니다. 예약 실행 결과는 다음 예약 시각 이후 같은 로그 명령으로 확인합니다. EC2를 재생성하면 예약도 다시 등록해야 합니다.

## 8. 초기 장소 준비

초기 데이터를 준비하려면 **EC2**에서 실행합니다. 이미 TourAPI 장소를 수집한 DB에서도 남은 초안을 공개할 수 있습니다.

```bash
bash scripts/init-places.sh
```

로컬 초기화와 같이 TourAPI 후보 500곳 수집, 검수 조건을 충족한 초기 장소 최대 5곳 공개, 지도 검증용 장소 최대 495곳 공개, 결과 조회 순서로 진행합니다. `.env`의 TourAPI 키를 사용합니다. 이미 수집하거나 공개한 장소는 건너뜁니다. 중간에 실패하면 원인을 해결한 뒤 같은 명령을 다시 실행합니다.

결과의 `publish_status = 1`인 장소를 브라우저에서 확인합니다. 예상 값과 달라 공개 조건을 만족하지 않은 장소는 `0`으로 남을 수 있습니다. 초기 공개는 기존 검수 SQL을 적용하는 작업이며 시설을 새로 검수하는 것은 아닙니다. 결과와 실패 대응은 [장소 스크립트 설명](../backend/docs/ec2-initial-places.md)을 참고합니다.

## 9. 인증서 백업 보관

**EC2**에서 새 백업을 만듭니다.

```bash
BACKUP_NAME="letsencrypt-backup-$(date +%Y%m%d-%H%M%S).tar.gz"
bash scripts/certificate-storage.sh backup "$HOME/$BACKUP_NAME"
echo "$BACKUP_NAME"
```

성공 메시지와 파일명을 확인합니다. **로컬 Mac**에서 출력된 파일명을 아래 값에 넣고 다운로드합니다.

```bash
BACKUP_NAME='EC2에서 출력된 파일명'
TLS_DIRECTORY='/Users/apple/Desktop/auth-keys/aws-auth-keys/04-team-project/tls'
umask 077
mkdir -p "$TLS_DIRECTORY"
chmod 700 "$TLS_DIRECTORY"
scp -i "$SSH_KEY" "ubuntu@$EC2_IP:~/$BACKUP_NAME" "$TLS_DIRECTORY/"
tar -tzf "$TLS_DIRECTORY/$BACKUP_NAME"
```

`tar -tzf`는 압축을 풀지 않고 파일 목록을 보여줍니다. `archive/`, `live/`, `accounts/`, `renewal/`이 포함되어 있는지 확인합니다. 백업은 프로젝트 밖에 보관하며 Git에 올리지 않습니다. 이후 인증서 갱신 후나 서버 삭제 전에도 [인증서 관리 안내](https.md)에 따라 최신 백업을 보관합니다.

## 기존 EC2의 앱 업데이트

새 이미지를 빌드·Push한 뒤 **EC2 · `~/outpick`**에서 실행합니다. 앱 코드만 바뀌었다면 파일 업로드·인증서 복원·데이터 수집은 반복하지 않습니다.

```bash
docker compose -f compose.deploy.yaml -f compose.https.yaml pull backend nginx
docker compose -f compose.deploy.yaml -f compose.https.yaml up -d --wait
docker compose -f compose.deploy.yaml -f compose.https.yaml ps
curl --fail -i -w '\n' http://localhost:18080/actuator/health
```

Frontend 이미지는 서비스 이름 `nginx`로 실행됩니다. Backend의 `UP` 응답을 확인하고 브라우저에서 변경한 기능을 확인합니다. 교체 중에는 잠시 요청이 실패할 수 있습니다.

설정·스크립트·SQL이 변경됐다면 2번의 목록에서 변경한 파일도 업로드합니다. `.env` 변경은 기존 서버 파일을 편집합니다. Nginx 설정만 바뀐 경우 두 파일을 지정한 Compose의 `exec -T nginx nginx -t`로 검사한 뒤 `exec -T nginx nginx -s reload`로 반영합니다.

오류는 같은 두 Compose 파일을 지정한 `logs --tail 100 backend nginx`로 확인합니다. 재배포를 위해 `down`할 필요는 없습니다. `down --volumes`는 DB와 인증서 볼륨을 삭제하므로 이 배포 절차에서 사용하지 않습니다.
