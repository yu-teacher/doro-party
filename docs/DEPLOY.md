# 도로 파티 배포 절차

도메인·IP·비밀값은 이 문서에 적지 않는다. 값은 서버의 `.env` 에만 둔다.

## 1. 사전 준비 (한 번만)
1. **DB**: 서버 Postgres 에 `CREATE DATABASE service_party;` (`POSTGRES_MULTIPLE_DATABASES` 는 빈 볼륨 최초 기동 때만 처리된다).
2. **MinIO 버킷**: `doro-party-media` 생성(읽기 전용 익명 접근은 객체 단위만, 목록 조회는 허용하지 않는다).
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
