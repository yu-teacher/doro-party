#!/usr/bin/env bash
# 도로 파티 CI 와 같은 검증을 로컬에서도 그대로 돌린다: 격리된 스택(Postgres/Redis/Guard/S3)을 올리고
# 백엔드·프런트 테스트를 실행한 뒤 스택을 내린다. 개발·운영 DB 는 건드리지 않는다.
#
#   scripts/ci-test.sh            # 전체
#   scripts/ci-test.sh backend    # 백엔드만
#   scripts/ci-test.sh web        # 프런트만 (스택 불필요)
#
# 필요: Docker, Java 25, Node 24, 형제 디렉터리 ../Doro (DORO_DIR 로 변경). 포트는 CI_* 환경변수로 바꿀 수 있다.
set -Eeuo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
TARGET="${1:-all}"
export DORO_DIR="${DORO_DIR:-../Doro}"
export CI_DB_PASSWORD="${CI_DB_PASSWORD:-ci-only-password}"
export CI_MINIO_SECRET="${CI_MINIO_SECRET:-ci-only-secret}"
CI_DB_PORT="${CI_DB_PORT:-25432}"
CI_GUARD_HTTP_PORT="${CI_GUARD_HTTP_PORT:-28081}"
CI_GUARD_GRPC_PORT="${CI_GUARD_GRPC_PORT:-9090}"
CI_MINIO_PORT="${CI_MINIO_PORT:-9000}"
export CI_DB_PORT CI_GUARD_HTTP_PORT CI_GUARD_GRPC_PORT CI_MINIO_PORT
STACK_TIMEOUT_SEC="${CI_STACK_TIMEOUT_SEC:-240}"
COMPOSE=(docker compose -p party-ci -f docker-compose.ci.yml)

# GitHub Actions 에서는 실패 원인을 주석(annotation)으로도 남긴다: 로그를 열지 않아도 실패 화면과 API 로 원인을 볼 수 있다.
LOG_FILE="$(mktemp)"
exec > >(tee -a "$LOG_FILE") 2>&1
report_failure() {
  local rc=$1
  if [ "$rc" -ne 0 ] && [ "${GITHUB_ACTIONS:-}" = true ]; then
    printf '::error title=ci-test.sh failed (exit %s)::%s\n' "$rc" "$(tail -n 25 "$LOG_FILE" | sed 's/%/%25/g' | awk '{printf "%s%%0A", $0}')"
  fi
}
trap 'report_failure $?' EXIT

log() { printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*"; }
die() { log "ERROR: $*"; exit 1; }

run_web() {
  log "== 프런트: 린트 · 타입 · 테스트 =="
  (cd web && npm ci --no-audit --no-fund && npm run lint && npx tsc -b && npm test)
}

run_backend() {
  [ -d "$DORO_DIR/guard" ] || die "$DORO_DIR/guard 가 없다. Doro 레포를 형제 디렉터리에 체크아웃한다 (DORO_DIR 로 경로 지정)"
  # 실패했을 때만 Guard 로그를 보여 주고, 어떤 경우든 스택은 내린다
  trap 'rc=$?; [ $rc -eq 0 ] || "${COMPOSE[@]}" logs --tail 60 guard-api >&2 || true; "${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true; report_failure $rc' EXIT
  log "== 격리 스택 기동 (Postgres/Redis/Guard/S3) =="
  "${COMPOSE[@]}" up -d --build
  deadline=$((SECONDS + STACK_TIMEOUT_SEC))
  until curl -fsS "http://127.0.0.1:${CI_GUARD_HTTP_PORT}/actuator/health" >/dev/null 2>&1; do
    [ $SECONDS -lt $deadline ] || die "Guard 가 ${STACK_TIMEOUT_SEC}초 안에 healthy 가 되지 않았다"
    sleep 3
  done
  log "== 백엔드 테스트 =="
  DB_HOST=127.0.0.1 DB_NAME=service_party DB_PORT="$CI_DB_PORT" DB_USER=doro_admin DB_PASSWORD="$CI_DB_PASSWORD" \
  GUARD_GRPC_HOST=127.0.0.1 GUARD_GRPC_PORT="$CI_GUARD_GRPC_PORT" \
  GUARD_HTTP_HOST=127.0.0.1 GUARD_HTTP_PORT="$CI_GUARD_HTTP_PORT" \
  MINIO_ENDPOINT="http://127.0.0.1:${CI_MINIO_PORT}" MINIO_SECRET_KEY="$CI_MINIO_SECRET" \
    ./gradlew cleanTest test --console=plain
}

case "$TARGET" in
  all) run_web; run_backend ;;
  web) run_web ;;
  backend) run_backend ;;
  *) echo "사용법: scripts/ci-test.sh [all|backend|web]" >&2; exit 2 ;;
esac
log "통과"
