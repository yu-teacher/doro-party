#!/usr/bin/env bash
# 서버(mini)에서 실행한다: 파티의 알림 서비스 이관 검증(SHADOW)을 켠다(또는 끈다). 여러 번 실행해도 결과가 같다(멱등). 토큰 값은 화면에 출력하지 않는다.
#
#   ssh mini 'bash -s -- --check'             < scripts/enable-notify-shadow.sh   # 읽기 전용: 현재 상태(기본)
#   ssh mini 'bash -s -- --apply'             < scripts/enable-notify-shadow.sh   # 설정 파일만 고친다(컨테이너는 그대로)
#   ssh mini 'bash -s -- --apply --restart'   < scripts/enable-notify-shadow.sh   # 위 + 파티 api 컨테이너를 다시 만든다(몇 초)
#   ssh mini 'bash -s -- --off --restart'     < scripts/enable-notify-shadow.sh   # SHADOW 를 끈다(LEGACY: 파티가 직접 보내고 알림 서비스를 부르지 않는다)
#
# 하는 일
#   1) ~/doro-notify/.env 의 NOTIFY_INTERNAL_TOKENS 에서 'party:' 토큰을 읽어(출력 안 함) ~/doro-party/.env 의 PARTY_NOTIFY_TOKEN 에 넣고 PARTY_NOTIFY_MODE=SHADOW 로 한다(백업 후).
#   2) ~/doro-party/docker-compose.prod.yml 이 두 변수를 컨테이너에 전달하지 않으면 PARTY_VAPID_SUBJECT 줄 바로 뒤에 두 줄을 넣는다(백업 후).
#   3) (--restart) 파티 api 컨테이너만 다시 만들고 healthy 를 기다린다.
# 하지 않는 일: 알림 서비스 설정 변경, 파티 푸시 키 변경, 파티 코드 배포. SHADOW 에서도 알림은 지금처럼 파티가 보낸다(알림 서비스는 판정만 한다).
# 환경변수(리허설·점검용, 기본값은 운영): PARTY_DIR, NOTIFY_DIR
set -Eeuo pipefail

PARTY_DIR="${PARTY_DIR:-$HOME/doro-party}"
NOTIFY_DIR="${NOTIFY_DIR:-$HOME/doro-notify}"
APPLY=false; OFF=false; RESTART=false
for a in "$@"; do
  case "$a" in
    --check) ;;
    --apply) APPLY=true ;;
    --off) OFF=true; APPLY=true ;;
    --restart) RESTART=true ;;
    *) echo "사용법: bash -s -- [--check | --apply | --off] [--restart]" >&2; exit 2 ;;
  esac
done
log() { printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*"; }
die() { log "ERROR: $*"; exit 1; }
get_var() { grep -m1 "^$2=" "$1" 2>/dev/null | cut -d= -f2- || true; }
set_var() {
  local file="$1" key="$2" value="$3" tmp
  tmp="$(mktemp "$file.XXXXXX")"
  grep -v "^$key=" "$file" > "$tmp" || true
  printf '%s=%s\n' "$key" "$value" >> "$tmp"
  chmod 600 "$tmp"; mv "$tmp" "$file"
}
ENV_FILE="$PARTY_DIR/.env"; COMPOSE="$PARTY_DIR/docker-compose.prod.yml"; NOTIFY_ENV="$NOTIFY_DIR/.env"
[ -f "$ENV_FILE" ] && [ -f "$COMPOSE" ] || die "$PARTY_DIR 의 .env 또는 docker-compose.prod.yml 이 없다"
passes() { grep -q "PARTY_NOTIFY_TOKEN" "$COMPOSE" && grep -q "PARTY_NOTIFY_MODE" "$COMPOSE"; }
party_token_in_notify() { get_var "$NOTIFY_ENV" NOTIFY_INTERNAL_TOKENS | tr ',' '\n' | grep -m1 '^party:' | cut -d: -f2- || true; }

describe() {
  local mode tok ntok
  mode="$(get_var "$ENV_FILE" PARTY_NOTIFY_MODE)"; tok="$(get_var "$ENV_FILE" PARTY_NOTIFY_TOKEN)"; ntok="$(party_token_in_notify)"
  log "파티 .env: PARTY_NOTIFY_MODE=${mode:-(없음=LEGACY)} | 토큰 $([ -n "$tok" ] && echo "있음(${#tok}자)" || echo 없음) | compose 전달: $(passes && echo 예 || echo 아니오)"
  log "알림 서비스 .env 의 party 호출자 토큰: $([ -n "$ntok" ] && echo "있음(${#ntok}자)" || echo 없음)"
  if [ -n "$tok" ] && [ -n "$ntok" ]; then [ "$tok" = "$ntok" ] && log "두 토큰이 같다" || log "경고: 파티 쪽 토큰과 알림 서비스 쪽 토큰이 다르다"; fi
  log "파티 컨테이너: $(docker inspect doro-party-api --format '{{.State.Status}}/{{.State.Health.Status}} (시작 {{.State.StartedAt}})' 2>/dev/null || echo 없음)"
}
if [ "$APPLY" != true ]; then describe; exit 0; fi

TS="$(date +%Y%m%d-%H%M%S)"
if [ "$OFF" = true ]; then
  cp -p "$ENV_FILE" "$ENV_FILE.bak-notify-$TS"
  set_var "$ENV_FILE" PARTY_NOTIFY_MODE LEGACY
  log "PARTY_NOTIFY_MODE=LEGACY 로 되돌렸다(백업: $ENV_FILE.bak-notify-$TS). 토큰은 그대로 둔다."
else
  NTOK="$(party_token_in_notify)"
  [ -n "$NTOK" ] || die "$NOTIFY_ENV 의 NOTIFY_INTERNAL_TOKENS 에 'party:' 항목이 없다(알림 서비스의 issue-caller-tokens.sh 를 먼저)"
  [ "${#NTOK}" -ge 32 ] || die "party 호출자 토큰이 32자 미만이다"
  cp -p "$ENV_FILE" "$ENV_FILE.bak-notify-$TS"; log ".env 백업: $ENV_FILE.bak-notify-$TS"
  set_var "$ENV_FILE" PARTY_NOTIFY_TOKEN "$NTOK"
  set_var "$ENV_FILE" PARTY_NOTIFY_MODE SHADOW
  unset NTOK
  log "파티 .env 에 PARTY_NOTIFY_TOKEN(값 비출력)과 PARTY_NOTIFY_MODE=SHADOW 를 넣었다"
fi
if passes; then log "compose 가 이미 두 변수를 전달한다"
else
  cp -p "$COMPOSE" "$COMPOSE.bak-notify-$TS"
  awk '
    /^[ ]+PARTY_VAPID_SUBJECT:/ && !done { print; match($0, /^[ ]+/); ind=substr($0, 1, RLENGTH);
      print ind "PARTY_NOTIFY_MODE: ${PARTY_NOTIFY_MODE:-LEGACY}"; print ind "PARTY_NOTIFY_TOKEN: ${PARTY_NOTIFY_TOKEN:-}"; done=1; next }
    { print }' "$COMPOSE.bak-notify-$TS" > "$COMPOSE"
  if passes; then log "compose 에 전달 줄 두 개를 추가했다(백업: $COMPOSE.bak-notify-$TS)"
  else cp -p "$COMPOSE.bak-notify-$TS" "$COMPOSE"; die "compose 에 PARTY_VAPID_SUBJECT 줄이 없어 전달 줄을 넣지 못했다(되돌렸다)"; fi
fi
if [ "$RESTART" = true ]; then
  log "파티 api 컨테이너를 다시 만든다(몇 초)"
  ( cd "$PARTY_DIR" && docker compose -f docker-compose.prod.yml up -d --no-build --no-deps --force-recreate party-api </dev/null ) >/dev/null 2>&1 || die "컨테이너를 다시 만들지 못했다"
  for _ in $(seq 1 40); do
    [ "$(docker inspect doro-party-api --format '{{.State.Health.Status}}' 2>/dev/null)" = healthy ] && { log "doro-party-api 가 healthy 로 돌아왔다"; describe; exit 0; }
    sleep 3
  done
  die "doro-party-api 가 healthy 로 돌아오지 않았다(.env 백업: $ENV_FILE.bak-notify-$TS)"
fi
log "컨테이너는 그대로다. 새 값은 --restart 또는 다음 배포 때 반영된다."
describe
