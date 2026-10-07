#!/usr/bin/env bash
# 서버(mini)의 셀프호스티드 러너가 실행하는 배포: 컨테이너 안에서 빌드 -> 롤백 스냅샷 + DB 백업 -> 반영 -> 헬스체크 -> 실패하면 자동 복구.
# 서버에는 Java 21 만 있어서 Java 25 / Node 24 는 Docker 이미지로 가져온다. 서버의 docker-compose.prod.yml 과 .env 는 덮어쓰지 않는다.
#
#   scripts/deploy-on-server.sh <doro-party 체크아웃> <Doro 체크아웃>
#   scripts/deploy-on-server.sh --dry-run <...>   # 사전 점검만
set -Eeuo pipefail

DRY_RUN=false
if [ "${1:-}" = "--dry-run" ]; then DRY_RUN=true; shift; fi
PARTY_SRC="$(cd "${1:?doro-party 체크아웃 경로가 필요하다}" && pwd)"
DORO_SRC="$(cd "${2:?Doro 체크아웃 경로가 필요하다}" && pwd)"
[ "$(dirname "$PARTY_SRC")" = "$(dirname "$DORO_SRC")" ] || { echo "두 체크아웃은 같은 부모 디렉터리 아래 형제여야 한다 (settings.gradle 의 includeBuild('../Doro'))" >&2; exit 2; }
PARENT="$(dirname "$PARTY_SRC")"; PARTY_NAME="$(basename "$PARTY_SRC")"

REMOTE_DIR="${DEPLOY_DIR:-$HOME/doro-party}"
COMPOSE="docker-compose.prod.yml"
JAR_REL="build/libs/doro-party-0.0.1-SNAPSHOT.jar"
API_PORT="${PARTY_API_PORT:-8086}"
WEB_PORT="${PARTY_WEB_PORT:-3005}"
HEALTH_TIMEOUT_SEC="${HEALTH_TIMEOUT_SEC:-120}"
JAVA_IMAGE="${CI_JAVA_IMAGE:-gradle:9.5.1-jdk25}"
NODE_IMAGE="${CI_NODE_IMAGE:-node:24-alpine}"
# 다른 서비스 배포와 Gradle 캐시를 나누어 쓴다: 같은 캐시 폴더를 동시에 쓰면 잠금 대기로 빌드가 실패할 수 있다.
CACHE="${CI_CACHE_DIR:-$HOME/.cache/party-deploy}"
TS="$(date +%Y%m%d-%H%M%S)"
SNAP="$HOME/backups/pre-party-deploy-$TS"
LOG_DIR="$HOME/logs"; mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/party-deploy-$TS.log"
exec > >(tee -a "$LOG_FILE") 2>&1
on_exit() {
  local rc=$?
  if [ "$rc" -ne 0 ] && [ "${GITHUB_ACTIONS:-}" = true ]; then
    printf '::error title=deploy-on-server.sh failed (exit %s)::%s\n' "$rc" "$(tail -n 25 "$LOG_FILE" | sed 's/%/%25/g' | awk '{printf "%s%%0A", $0}')"
  fi
}
trap on_exit EXIT
log() { printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*"; }
die() { log "ERROR: $*"; exit 1; }

log "== 사전 점검 =="
[ -f "$REMOTE_DIR/$COMPOSE" ] && [ -f "$REMOTE_DIR/.env" ] || die "$REMOTE_DIR/$COMPOSE 또는 .env 가 없다 (docs/DEPLOY.md 의 서버 최초 준비를 먼저 한다)"
[ -x "$HOME/ops/backup.sh" ] || die "$HOME/ops/backup.sh 가 없다 (DB 백업 없이 배포하지 않는다)"
docker info >/dev/null 2>&1 || die "docker 를 사용할 수 없다"
# 카카오 지도 키는 웹 빌드 시점에 번들에 들어간다. 저장소에는 두지 않고 서버의 .env 에서만 읽는다(값은 로그에 남기지 않는다).
KAKAO_KEY="$(grep -E '^VITE_KAKAO_MAP_APP_KEY=' "$REMOTE_DIR/.env" | head -1 | cut -d= -f2- || true)"
[ -n "$KAKAO_KEY" ] || die "$REMOTE_DIR/.env 에 VITE_KAKAO_MAP_APP_KEY 가 없다"
log "  commit: $(git -C "$PARTY_SRC" rev-parse --short HEAD 2>/dev/null || echo '?'), 대상: $REMOTE_DIR"
if [ "$DRY_RUN" = true ]; then log "--dry-run: 여기서 종료"; exit 0; fi

mkdir -p "$CACHE/gradle" "$CACHE/npm"
LIMITS=(--cpus "${CI_CPUS:-2}" --memory "${CI_MEMORY:-4g}")
COMMON=(--rm "${LIMITS[@]}" --user "$(id -u):$(id -g)" -e HOME=/tmp -v "$CACHE:/cache" -v "$PARENT:/work")

log "== 빌드: 백엔드 jar (테스트는 CI 에서 통과한 커밋만 여기까지 온다) =="
docker run "${COMMON[@]}" -e GRADLE_USER_HOME=/cache/gradle -w "/work/$PARTY_NAME" "$JAVA_IMAGE" \
  gradle clean bootJar -x test --no-daemon --console=plain
log "== 빌드: 웹 =="
docker run "${COMMON[@]}" -e npm_config_cache=/cache/npm -e VITE_KAKAO_MAP_APP_KEY="$KAKAO_KEY" -w "/work/$PARTY_NAME/web" "$NODE_IMAGE" \
  sh -ec 'npm ci --no-audit --no-fund && npm run build'
[ -f "$PARTY_SRC/$JAR_REL" ] || die "$JAR_REL 이 만들어지지 않았다"
[ -d "$PARTY_SRC/web/dist" ] || die "web/dist 가 만들어지지 않았다"

log "== 롤백 스냅샷 + DB 백업 =="
mkdir -p "$SNAP"
cp -a "$REMOTE_DIR/build/libs" "$SNAP/libs" 2>/dev/null || true
cp -a "$REMOTE_DIR/web/dist" "$SNAP/dist" 2>/dev/null || true
for svc in api web; do
  img="$(docker inspect --format '{{.Image}}' "doro-party-$svc" 2>/dev/null || true)"
  [ -n "$img" ] && docker tag "$img" "doro-party-rollback:$svc-$TS" && echo "$svc $img" >> "$SNAP/images.txt"
done
"$HOME/ops/backup.sh" backup | tail -2

apply_release() {  # $1=jar 경로, $2=dist 경로
  mkdir -p "$REMOTE_DIR/build/libs" "$REMOTE_DIR/web/dist"
  cp -f "$1" "$REMOTE_DIR/build/libs/doro-party-0.0.1-SNAPSHOT.jar"
  rsync -a --delete "$2/" "$REMOTE_DIR/web/dist/"
  (cd "$REMOTE_DIR" && docker compose -f "$COMPOSE" up -d --build party-api party-web) 2>&1 | tail -5
}
wait_healthy() {
  local deadline=$((SECONDS + HEALTH_TIMEOUT_SEC))
  until [ "$(docker inspect --format '{{.State.Health.Status}}' doro-party-api 2>/dev/null || echo none)" = healthy ]; do
    [ $SECONDS -lt $deadline ] || return 1
    sleep 3
  done
  curl -fsS -o /dev/null "http://127.0.0.1:${API_PORT}/actuator/health" && curl -fsS -o /dev/null "http://127.0.0.1:${WEB_PORT}/party/"
}

log "== 반영 (compose 파일과 .env 는 보내지 않는다) =="
cp -f "$PARTY_SRC/Dockerfile" "$REMOTE_DIR/Dockerfile"
mkdir -p "$REMOTE_DIR/web"
cp -f "$PARTY_SRC/web/nginx.conf" "$PARTY_SRC/web/Dockerfile" "$REMOTE_DIR/web/"
apply_release "$PARTY_SRC/$JAR_REL" "$PARTY_SRC/web/dist"

log "== 헬스체크 (최대 ${HEALTH_TIMEOUT_SEC}s) =="
if wait_healthy; then
  log "완료. 롤백 지점: $SNAP, 이미지 태그 doro-party-rollback:{api,web}-$TS"
  exit 0
fi

log "ERROR: 헬스체크 실패. 직전 릴리스로 자동 복구한다."
docker logs --tail 40 doro-party-api || true
if [ -d "$SNAP/libs" ] && [ -d "$SNAP/dist" ]; then
  apply_release "$SNAP/libs/doro-party-0.0.1-SNAPSHOT.jar" "$SNAP/dist"
  if wait_healthy; then log "복구 완료: 직전 릴리스가 서비스 중이다."; else log "ERROR: 복구 후에도 헬스체크 실패. 수동 확인 필요 ($SNAP)."; fi
else
  log "ERROR: 직전 릴리스 스냅샷이 없어(첫 배포) 자동 복구할 수 없다. 수동 확인 필요."
fi
exit 1
