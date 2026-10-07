# 도로 파티 배포 절차

도메인·IP·비밀값은 이 문서에 적지 않는다. 값은 서버의 `.env` 에만 둔다.

## 1. 사전 준비 (한 번만)
1. **DB**: 서버 Postgres 에 `CREATE DATABASE service_party;` (`POSTGRES_MULTIPLE_DATABASES` 는 빈 볼륨 최초 기동 때만 처리된다).
2. **MinIO 버킷**: `doro-party-media`. 앱이 첫 사용 때 **비공개**로 만든다(공개 읽기 정책을 걸지 않는다). 사진은 백엔드를 거쳐서만 내려가므로 게이트웨이에 공개 경로가 없다.
3. **OAuth 클라이언트 등록**: `doro-party`, redirect URI `https://<공개 도메인>/party/api/v1/bff/callback`, 자사 클라이언트(동의 화면 생략). `doro-blog/scripts/setup-bff-server.sh` 와 같은 방식.
4. **환경변수**: `.env.example` 를 참고해 서버의 `.env` 작성. `PARTY_SESSION_KEY` 는 `openssl rand -base64 32` 로 만들어 비밀번호 관리자에 보관한다(바꾸면 기존 세션이 모두 무효가 된다).
5. **Guard 서비스 토큰**: 서비스별 토큰 발급(`schema-write` 권한 포함). 공유 토큰을 쓰지 않는다.

## 2. 게이트웨이
`deploy/gateway-party.conf` 를 `gateway/nginx.conf` 에 반영한다(upstream + `/party/` location). 기존 파일을 백업하고 `nginx -t` 통과 후 reload.

## 3. 빌드와 기동
1. 로컬: `./gradlew clean bootJar` 와 `cd web && npm ci && npm run build`.
2. 서버로 `build/libs/doro-party-0.0.1-SNAPSHOT.jar`, `web/dist/`, `web/nginx.conf`, `web/Dockerfile`, `Dockerfile`, `docker-compose.prod.yml` 전송.
3. `docker compose -f docker-compose.prod.yml up -d --build party-api party-web`.
4. `healthy` 확인 후 점검(아래).

## 4. 점검
- `https://<도메인>/party/` 가 열리고 manifest·service worker 가 등록된다.
- `/party/api/v1/bff/login` 이 Doro 로그인 화면으로 이동하고, 로그인 후 `/party/` 로 돌아온다.
- `/party/api/v1/bff/session` 응답이 로그인 상태를 반영한다.
- 기존 서비스(블로그 `/`, 포털, 메뉴)가 그대로 동작한다.

## 5. 롤백
이전 jar 와 `web/dist` 를 보관해 두었다가 복원 후 재빌드한다. 게이트웨이는 백업한 `nginx.conf` 로 되돌려 reload.

## 자동 배포 (GitHub Actions + 서버의 셀프호스티드 러너)

`main` 에 push 하면 CI(`.github/workflows/ci.yml`)가 프런트(린트·타입·테스트·빌드)와 백엔드(격리된 Postgres/Guard/S3 스택에서 테스트)를 돌린다.
CI 가 통과한 커밋만 `deploy.yml` 이 서버 러너에서 `scripts/deploy-on-server.sh` 로 배포한다(빌드 → 롤백 스냅샷·DB 백업 → 반영 → 헬스체크 → 실패 시 자동 복구).

서버 최초 준비(한 번만):
1. 위의 DB·OAuth 클라이언트·게이트웨이 준비를 마친다.
2. `~/doro-party/` 에 `docker-compose.prod.yml` 과 `.env` 를 둔다(`.env` 에는 `PARTY_*`, `DB_PASSWORD`, `MINIO_ROOT_PASSWORD`, 그리고 웹 빌드용 `VITE_KAKAO_MAP_APP_KEY` 를 넣는다). 배포 스크립트는 이 두 파일을 덮어쓰지 않는다.
3. 이 저장소에 러너를 등록하고(저장소 설정 → Actions → Runners) 저장소 변수 `CD_ENABLED=true` 를 만든다. 이 변수가 없으면 배포 작업은 건너뛴다.
4. 사전 점검: `scripts/deploy-on-server.sh --dry-run <party 체크아웃> <Doro 체크아웃>`

로컬에서 CI 와 같은 검증: `scripts/ci-test.sh` (Docker, Java 25, Node 24, 형제 디렉터리 `../Doro` 필요).
