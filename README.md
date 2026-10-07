# 도로 파티 (Doro Party)

친구들과 각자 만든 지도를 겹쳐 보고 "오늘 어디 가지?"를 정하는 모바일 우선 PWA.
[Doro 플랫폼](https://github.com/yu-teacher/doro)(IAM·Guard·SDK) 위의 서브 서비스입니다.

- 설계: [docs/DESIGN.md](docs/DESIGN.md)
- 배포: [docs/DEPLOY.md](docs/DEPLOY.md)

## 구성
| 위치 | 내용 |
|---|---|
| `src/` | Spring Boot 4 / Java 25 백엔드 (Doro OAuth BFF 로그인, Guard ReBAC, Flyway) |
| `web/` | React 19 + Vite PWA (카카오맵), `/party/` 하위 경로에 마운트 |
| `deploy/` | 게이트웨이(nginx) 설정 조각 |

## 개발
```bash
cp .env.example .env            # 값 채우기
./gradlew test                  # 백엔드 테스트 (로컬 Doro 스택 필요: Postgres, Guard)
./gradlew bootRun               # API :8086
cd web && npm ci && npm run dev # 웹 :3005 → http://localhost:3005/party/
```
`VITE_KAKAO_MAP_APP_KEY` 가 없으면 지도 대신 안내가 표시됩니다(`web/.env.local` 에 둡니다. 커밋되지 않습니다).

### 로컬에서 로그인까지 써보기
운영에서는 게이트웨이가 `/oauth2/consent` 를 포털로 연결하지만 로컬에는 게이트웨이가 없습니다.
로컬 Doro 의 포털 개발 컨테이너(3010)가 IAM 프록시와 로그인 화면을 함께 서빙하므로, 인가 주소만 그쪽으로 돌립니다.
```bash
PARTY_OAUTH_AUTHORIZE_URL=http://localhost:3010/oauth2/authorize PARTY_COOKIE_SECURE=false ./gradlew bootRun
```
로컬 IAM 에 OAuth 클라이언트 `doro-party`(redirect URI `http://localhost:3005/party/api/v1/bff/callback`, 자사 앱)를 등록해 두어야 합니다.
