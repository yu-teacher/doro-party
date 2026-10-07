#!/usr/bin/env bash
# 서버(mini)에서 실행한다: deploy/gateway-party.conf 의 upstream·location 을 게이트웨이 nginx.conf 에 넣는다. 여러 번 실행해도 결과가 같다(멱등).
# 게이트웨이는 compose 밖에 있고 nginx.conf 를 파일 하나로 마운트하므로 같은 inode 로 덮어쓴다. 반영 전에 컨테이너 안에서 새 설정을 먼저 검증하고(nginx -t),
# 실패하면 아무것도 바꾸지 않는다. reload 뒤 확인이 실패하면 백업으로 되돌린다.
#
#   scp deploy/gateway-party.conf mini:doro-party/gateway-party.conf
#   ssh mini 'bash -s -- --dry-run'            < scripts/apply-gateway-party.sh   # 만들어질 설정을 검증만
#   ssh mini 'bash -s -- --apply --report-only' < scripts/apply-gateway-party.sh  # CSP 를 Report-Only 로 시험 반영(권장: 처음)
#   ssh mini 'bash -s -- --apply'              < scripts/apply-gateway-party.sh   # CSP 강제 모드
set -Eeuo pipefail

CONF="${GATEWAY_CONF:-$HOME/doro/gateway/nginx.conf}"
PIECE="${PARTY_PIECE:-$HOME/doro-party/gateway-party.conf}"
CONTAINER="${GATEWAY_CONTAINER:-doro-gateway}"
APPLY=false; REPORT_ONLY=false
for a in "$@"; do
  case "$a" in
    --apply) APPLY=true ;;
    --dry-run) APPLY=false ;;
    --report-only) REPORT_ONLY=true ;;
    *) echo "알 수 없는 옵션: $a" >&2; exit 2 ;;
  esac
done
log() { printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*"; }
die() { log "ERROR: $*"; exit 1; }
[ -f "$CONF" ] || die "$CONF 가 없다"
[ -f "$PIECE" ] || die "$PIECE 가 없다 (scp 로 먼저 복사한다)"
docker inspect "$CONTAINER" >/dev/null 2>&1 || die "$CONTAINER 컨테이너가 없다"

TS="$(date +%Y%m%d-%H%M%S)"
NEW="$(mktemp)"; trap 'rm -f "$NEW"' EXIT
STATE="$(python3 - "$CONF" "$PIECE" "$NEW" "$REPORT_ONLY" <<'PY'
import re, sys
conf_path, piece_path, out_path, report_only = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4] == "true"
conf = open(conf_path, encoding="utf-8").read()
piece = open(piece_path, encoding="utf-8").read()
piece = "\n".join(l for l in piece.splitlines() if not l.lstrip().startswith("#"))  # 주석 줄은 넣지 않는다

upstreams = re.findall(r"upstream\s+\w+\s*\{[^}]*\}", piece)
locations = re.findall(r"location\s+[^{]+\{[^}]*\}", piece)
if len(upstreams) != 2 or len(locations) != 3:
    sys.exit(f"조각 파일 구조가 예상과 다르다(upstream {len(upstreams)}, location {len(locations)})")

def indent(block, n):
    pad = " " * n
    lines = block.strip().splitlines()
    out = [pad + lines[0].strip()]
    for l in lines[1:]:
        s = l.strip()
        out.append("" if not s else pad + ("" if s == "}" else "    ") + s)
    return "\n".join(out) + "\n"

if "party_web_upstream" in conf:
    print("already"); open(out_path, "w", encoding="utf-8").write(conf); sys.exit(0)

if report_only:
    locations = [l.replace("add_header Content-Security-Policy ", "add_header Content-Security-Policy-Report-Only ") for l in locations]

m = re.search(r"upstream\s+menu_upstream\s*\{[^}]*\}\n", conf)
if not m: sys.exit("menu_upstream 블록을 찾지 못했다")
conf = conf[:m.end()] + "\n" + "\n".join(indent(u, 4) for u in upstreams) + conf[m.end():]
# 번호가 붙은 구획 주석("# 8. DORO Menu ...") 바로 위에 넣어 주석과 블록이 어긋나지 않게 한다. 없으면 location /menu/ 위.
anchor = "        # 8. DORO Menu Frontend"
if conf.count(anchor) != 1:
    anchor = "        location /menu/ {"
if conf.count(anchor) != 1: sys.exit("삽입 위치(# 8. DORO Menu / location /menu/)를 정확히 하나 찾지 못했다")
block = "        # 도로 파티 (/party/): deploy/gateway-party.conf 에서 옴\n" + "\n".join(indent(l, 8) for l in locations) + "\n"
conf = conf.replace(anchor, block + anchor, 1)
open(out_path, "w", encoding="utf-8").write(conf)
print("changed")
PY
)"
[ "$STATE" = already ] && { log "이미 반영돼 있다(party_web_upstream 존재). 변경 없음"; exit 0; }
log "새 설정 생성됨 (CSP: $([ "$REPORT_ONLY" = true ] && echo Report-Only || echo 강제))"

# 컨테이너 안에서 검증: 실제 인증서 경로·upstream 이름 해석까지 같은 환경에서 확인한다
docker cp "$NEW" "$CONTAINER:/tmp/nginx-party-test.conf"
if docker exec "$CONTAINER" nginx -t -c /tmp/nginx-party-test.conf; then log "새 설정 검증 통과(nginx -t)"; else docker exec "$CONTAINER" rm -f /tmp/nginx-party-test.conf; die "새 설정 검증 실패. 아무것도 바꾸지 않았다"; fi
docker exec "$CONTAINER" rm -f /tmp/nginx-party-test.conf
log "변경 내용(diff):"; diff -u "$CONF" "$NEW" | sed -n '1,400p' | grep -E '^[+-]' | grep -vE '^(\+\+\+|---)' | cut -c1-110 | head -60 || true
[ "$APPLY" = true ] || { log "--dry-run: 여기서 종료 (--apply 로 반영)"; exit 0; }

cp -p "$CONF" "$CONF.bak-party-$TS"
cat "$NEW" > "$CONF"   # 같은 inode 로 덮어쓴다(컨테이너가 이 파일을 바로 마운트한다)
if docker exec "$CONTAINER" nginx -t && docker exec "$CONTAINER" nginx -s reload; then
  sleep 1
  code="$(curl -sk -o /dev/null -w '%{http_code}' --resolve "localhost:443:127.0.0.1" https://localhost/party/ || echo 000)"
  api="$(curl -sk -o /dev/null -w '%{http_code}' --resolve "localhost:443:127.0.0.1" https://localhost/party/api/v1/me || echo 000)"
  portal="$(curl -sk -o /dev/null -w '%{http_code}' --resolve "localhost:443:127.0.0.1" https://localhost/ || echo 000)"
  blog="$(curl -sk -o /dev/null -w '%{http_code}' --resolve "localhost:443:127.0.0.1" 'https://localhost/api/v1/posts?page=0&size=1' || echo 000)"
  log "확인: /party/=$code (200 기대), /party/api/v1/me=$api (401 기대), 포털 /=$portal, 블로그 API=$blog (200 기대)"
  if [ "$code" = 200 ] && [ "$api" = 401 ] && [ "$blog" = 200 ]; then log "완료. 백업: $CONF.bak-party-$TS"; exit 0; fi
  log "ERROR: 확인 실패. 백업으로 되돌린다"
else
  log "ERROR: 반영 중 nginx 검증/reload 실패. 백업으로 되돌린다"
fi
cat "$CONF.bak-party-$TS" > "$CONF"
docker exec "$CONTAINER" nginx -t && docker exec "$CONTAINER" nginx -s reload && log "복구 완료" || log "ERROR: 복구 후에도 이상. 수동 확인 필요: $CONF.bak-party-$TS"
exit 1
