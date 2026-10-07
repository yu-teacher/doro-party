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
`VITE_KAKAO_MAP_APP_KEY` 가 없으면 지도 대신 안내가 표시됩니다.
