# 도로 파티 배포 절차

도메인·IP·비밀값은 이 문서에 적지 않는다. 값은 서버의 `.env` 에만 둔다.

## 1. 사전 준비 (한 번만)
`scripts/setup-party-server.sh` 가 아래를 한 번에 한다(멱등, 비밀 값은 출력하지 않는다). 먼저 `--dry-run` 으로 계획을 본 뒤 `--apply`.

```bash
ssh <서버> 'bash -s -- --origin https://<공개 도메인> --dry-run' < scripts/setup-party-server.sh
ssh <서버> 'VITE_KAKAO_MAP_APP_KEY=<키> bash -s -- --origin https://<공개 도메인> --apply' < scripts/setup-party-server.sh
scp docker-compose.prod.yml <서버>:doro-party/
```

1. **DB**: Postgres 에 `service_party` 생성(`POSTGRES_MULTIPLE_DATABASES` 는 빈 볼륨 최초 기동 때만 처리된다).
2. **MinIO 버킷**: `doro-party-media`. 앱이 첫 사용 때 **비공개**로 만든다. 사진은 백엔드를 거쳐서만 내려가므로 게이트웨이에 공개 경로가 없다.
3. **OAuth 클라이언트**: `doro-party`, redirect URI `https://<공개 도메인>/party/api/v1/bff/callback`, 자사 클라이언트(동의 화면 생략).
4. **서버 `.env`**: DB·MinIO 접속 정보, `PARTY_SESSION_KEY`(새 난수, 비밀번호 관리자에 보관. 바꾸면 모든 세션이 무효), `PARTY_OAUTH_*`, 카카오 지도 키(웹 빌드 때 번들에 들어간다).
5. **Guard 서비스 토큰**: 서비스별 토큰(`schema-write` 포함)을 Guard 설정에 추가하고 Guard 를 다시 만든다. 공유 토큰을 쓰지 않는다.

## 2. 게이트웨이
`scripts/apply-gateway-party.sh` 가 `deploy/gateway-party.conf` 의 upstream·location 을 게이트웨이 `nginx.conf` 에 넣는다. 새 설정을 컨테이너 안에서 먼저 `nginx -t` 로 검증하고, 반영 뒤 확인(`/party/` 200, API 401, 기존 서비스 200)이 실패하면 백업으로 자동 복구한다.

```bash
scp deploy/gateway-party.conf <서버>:doro-party/gateway-party.conf
ssh <서버> 'bash -s -- --dry-run'                    < scripts/apply-gateway-party.sh   # 검증만
ssh <서버> 'bash -s -- --apply --report-only'        < scripts/apply-gateway-party.sh   # CSP 를 위반 기록만 하는 모드로 시험 반영
ssh <서버> 'bash -s -- --apply --refresh'            < scripts/apply-gateway-party.sh   # 조각 파일 내용으로 다시 쓰고 CSP 강제
```

- CSP 는 `/party/` 에만 따로 둔다. 카카오 지도 SDK 때문에 `script-src` 에 `t1.kakaocdn.net` 과 `'unsafe-eval'` 이 필요하다(사용자 입력은 HTML 로 그리지 않는다).
- 순서: Report-Only 로 반영 → 지도·로그인을 써 보며 `/csp-report` 위반이 없는지 확인 → 강제 모드.
- PWA 서비스 워커는 HTML 을 응답 헤더째 저장하므로, 이미 방문한 기기에는 새 CSP 가 바로 적용되지 않는다. 확인할 때는 시크릿 탭이나 사이트 데이터 삭제 후 연다.

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

**배포 뒤 게이트웨이 reload**: 게이트웨이(nginx)는 upstream 컨테이너의 IP 를 시작·reload 때만 찾는다. 배포로 `party-api`/`party-web` 컨테이너가 새로 만들어져 IP 가 바뀌면 게이트웨이가 옛 주소로 연결해 `/party/` 가 502 가 된다. `deploy-on-server.sh` 는 헬스체크가 통과하면 `nginx -t` 후 `nginx -s reload` 를 하고(설정은 바꾸지 않고 진행 중인 요청은 끊지 않는다), 실패하면 배포를 실패로 표시한다. 컨테이너 이름이 다르면 `GATEWAY_CONTAINER` 로 지정한다. 수동 복구: `docker exec doro-gateway nginx -s reload`.

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
