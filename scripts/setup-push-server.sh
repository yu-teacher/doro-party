#!/usr/bin/env bash
# 서버(mini)에서 실행한다: 도로 파티 웹 푸시용 VAPID 키 쌍과 연락처를 안전하게 준비한다.
# 여러 번 실행해도 결과가 같다(멱등). 개인 키는 화면에 출력하지 않는다. 기본은 계획만 보여 주는 dry-run 이다.
#
#   ssh mini 'bash -s -- --subject mailto:me@example.com'          < scripts/setup-push-server.sh   # 계획만 출력
#   ssh mini 'bash -s -- --apply --subject mailto:me@example.com' < scripts/setup-push-server.sh   # 키 생성·배치(이미 있으면 그대로 둠)
#   ssh mini 'bash -s -- --apply --rotate'                         < scripts/setup-push-server.sh   # 키를 새로 만든다
#
# 하는 일
#   1) ~/doro-party/.env 에 PARTY_VAPID_PUBLIC_KEY / PARTY_VAPID_PRIVATE_KEY / PARTY_VAPID_SUBJECT 를 둔다. 이미 있으면 유지한다.
#      키는 Java(서버에 있는 JDK)로 P-256 키 쌍을 만들어 만든다. --rotate 면 새로 만든다(이미 등록된 기기의 구독은 무효가 되지만,
#      앱을 다시 열면 기기가 새 키로 자동 재구독한다).
#   2) ~/doro-party/docker-compose.prod.yml 의 party-api 환경변수에 위 세 값 전달을 넣는다(서버 전용 파일이라 제자리 수정).
#   3) 바꾸는 파일은 먼저 백업하고(.bak-push-<시각>), 고친 compose 를 검증한다.
#
# 연락처(subject)는 푸시 서비스가 문제가 있을 때 연락할 곳이다: mailto: 또는 https:// 로 시작해야 한다. 처음에는 --subject 가 꼭 필요하다.
#
# 반영: 이 스크립트 --apply 후 도로 파티를 배포(또는 party-api 재생성)하면 컨테이너가 새 환경변수를 읽는다.
# 확인: 앱의 알림 설정(종 아이콘)에서 "알림 받기" → "시험 알림 보내기".
set -Eeuo pipefail

PARTY_DIR="${PARTY_DIR:-$HOME/doro-party}"
ENV_FILE="$PARTY_DIR/.env"
COMPOSE="$PARTY_DIR/docker-compose.prod.yml"
APPLY=false; ROTATE=false; SUBJECT=""
while [ $# -gt 0 ]; do
  case "$1" in
    --apply) APPLY=true; shift ;;
    --rotate) ROTATE=true; shift ;;
    --subject) SUBJECT="${2:?--subject 값이 필요하다(mailto:... 또는 https://...)}"; shift 2 ;;
    *) echo "알 수 없는 옵션: $1" >&2; exit 2 ;;
  esac
done
TS="$(date +%Y%m%d-%H%M%S)"
log() { printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*"; }
die() { log "ERROR: $*"; exit 1; }

[ -f "$ENV_FILE" ] || die "$ENV_FILE 가 없다"
[ -f "$COMPOSE" ] || die "$COMPOSE 가 없다"
command -v python3 >/dev/null || die "python3 가 필요하다"

env_get() { grep -E "^$2=" "$1" 2>/dev/null | head -1 | cut -d= -f2- || true; }

CUR_PUB="$(env_get "$ENV_FILE" PARTY_VAPID_PUBLIC_KEY)"
CUR_PRIV="$(env_get "$ENV_FILE" PARTY_VAPID_PRIVATE_KEY)"
CUR_SUBJECT="$(env_get "$ENV_FILE" PARTY_VAPID_SUBJECT)"
[ -n "$SUBJECT" ] || SUBJECT="$CUR_SUBJECT"
if [ -n "$SUBJECT" ]; then
  case "$SUBJECT" in mailto:*|https://*) ;; *) die "연락처는 mailto: 또는 https:// 로 시작해야 한다" ;; esac
fi

need_keys=false; need_subject=false; need_compose=false
{ [ -z "$CUR_PUB" ] || [ -z "$CUR_PRIV" ] || [ "$ROTATE" = true ]; } && need_keys=true
[ "$SUBJECT" != "$CUR_SUBJECT" ] && need_subject=true
grep -q 'PARTY_VAPID_PUBLIC_KEY' "$COMPOSE" || need_compose=true
if [ -z "$SUBJECT" ]; then die "처음에는 --subject mailto:당신@메일 이 필요하다(푸시 서비스가 문제가 있을 때 연락할 곳)"; fi

log "계획: 키 새로 만듦=$need_keys (rotate=$ROTATE), 연락처 쓰기=$need_subject, compose 환경변수 추가=$need_compose"
if [ "$APPLY" != true ]; then log "--dry-run: 여기서 종료 (--apply 로 반영)"; exit 0; fi
if [ "$need_keys" = false ] && [ "$need_subject" = false ] && [ "$need_compose" = false ]; then
  log "이미 모두 준비돼 있다. 변경 없음"; exit 0
fi

# ---------------------------------------------------------------- 백업
cp -p "$ENV_FILE" "$ENV_FILE.bak-push-$TS"
cp -p "$COMPOSE" "$COMPOSE.bak-push-$TS"
log "백업: $ENV_FILE, $COMPOSE 를 *.bak-push-$TS 로 복사"

# ---------------------------------------------------------------- 키 생성(개인 키는 출력하지 않는다)
umask 077
NEW_PUB=""; NEW_PRIV=""
if [ "$need_keys" = true ]; then
  command -v java >/dev/null || die "키를 만들려면 java 가 필요하다"
  work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
  cat > "$work/VapidKeyGen.java" <<'JAVA'
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/** VAPID 키 쌍(P-256)을 만든다: 공개 키는 비압축 65바이트, 개인 키는 32바이트(둘 다 base64url). */
public class VapidKeyGen {
    static byte[] fixed32(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] out = new byte[32];
        int copy = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - copy, out, 32 - copy, copy);
        return out;
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();
        ECPublicKey pub = (ECPublicKey) pair.getPublic();
        byte[] encoded = new byte[65];
        encoded[0] = 4;
        System.arraycopy(fixed32(pub.getW().getAffineX()), 0, encoded, 1, 32);
        System.arraycopy(fixed32(pub.getW().getAffineY()), 0, encoded, 33, 32);
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        System.out.println(b64.encodeToString(encoded));
        System.out.println(b64.encodeToString(fixed32(((ECPrivateKey) pair.getPrivate()).getS())));
    }
}
JAVA
  keys="$(java "$work/VapidKeyGen.java")"
  NEW_PUB="$(printf '%s\n' "$keys" | sed -n 1p)"
  NEW_PRIV="$(printf '%s\n' "$keys" | sed -n 2p)"
  [ "${#NEW_PUB}" -eq 87 ] && [ "${#NEW_PRIV}" -eq 43 ] || die "키 생성 결과가 올바르지 않다"
  unset keys
fi

# KEY=VALUE 를 파일에 넣는다(있으면 그 줄만 바꾸고, 없으면 끝에 더한다). 값은 환경변수로만 넘겨 프로세스 목록에 남기지 않는다.
set_env() {  # $1=파일 $2=키 $3=값
  NEW_VALUE="$3" python3 - "$1" "$2" <<'PY'
import os, sys
path, key = sys.argv[1], sys.argv[2]
value = os.environ["NEW_VALUE"]
lines = open(path, encoding="utf-8").read().split("\n")
out, done = [], False
for line in lines:
    if line.startswith(key + "="):
        if not done:
            out.append(f"{key}={value}")
            done = True
        continue
    out.append(line)
if not done:
    while out and out[-1] == "":
        out.pop()
    out.append(f"{key}={value}")
    out.append("")
open(path, "w", encoding="utf-8").write("\n".join(out))
PY
}

if [ "$need_keys" = true ]; then
  set_env "$ENV_FILE" PARTY_VAPID_PUBLIC_KEY "$NEW_PUB"
  set_env "$ENV_FILE" PARTY_VAPID_PRIVATE_KEY "$NEW_PRIV"
  log "$ENV_FILE 에 VAPID 키 쌍을 썼다(개인 키는 출력하지 않는다)"
fi
if [ "$need_subject" = true ]; then
  set_env "$ENV_FILE" PARTY_VAPID_SUBJECT "$SUBJECT"
  log "$ENV_FILE 에 PARTY_VAPID_SUBJECT 를 썼다"
fi

# ---------------------------------------------------------------- compose (서버 전용 파일이라 제자리 수정)
if [ "$need_compose" = true ]; then
  python3 - "$COMPOSE" <<'PY'
import re, sys
path = sys.argv[1]
text = open(path, encoding="utf-8").read()
match = re.search(r"^(\s+)PARTY_SESSION_KEY: .*\n", text, re.M)
if match is None or len(re.findall(r"^\s+PARTY_SESSION_KEY: ", text, re.M)) != 1:
    sys.exit("compose 에서 삽입 위치(PARTY_SESSION_KEY)를 정확히 하나 찾지 못했다. 수동으로 확인해야 한다.")
indent = match.group(1)
block = (
    f"{indent}# 웹 푸시(알림). 키가 비어 있으면 푸시만 꺼진 채 앱은 그대로 동작한다. 값은 .env 에만 둔다.\n"
    f"{indent}PARTY_VAPID_PUBLIC_KEY: ${{PARTY_VAPID_PUBLIC_KEY:-}}\n"
    f"{indent}PARTY_VAPID_PRIVATE_KEY: ${{PARTY_VAPID_PRIVATE_KEY:-}}\n"
    f"{indent}PARTY_VAPID_SUBJECT: ${{PARTY_VAPID_SUBJECT:-}}\n"
)
open(path, "w", encoding="utf-8").write(text[:match.end()] + block + text[match.end():])
PY
  log "compose 의 party-api 환경변수에 VAPID 전달을 추가했다"
fi

# ---------------------------------------------------------------- 검증
if command -v docker >/dev/null 2>&1; then
  if (cd "$PARTY_DIR" && docker compose -f "$COMPOSE" config -q 2>/tmp/push-compose.err); then
    log "compose 검증 통과"
  else
    cat /tmp/push-compose.err >&2
    die "compose 검증 실패. 복구: cp $COMPOSE.bak-push-$TS $COMPOSE && cp $ENV_FILE.bak-push-$TS $ENV_FILE"
  fi
fi
[ -n "$(env_get "$ENV_FILE" PARTY_VAPID_PUBLIC_KEY)" ] && [ -n "$(env_get "$ENV_FILE" PARTY_VAPID_PRIVATE_KEY)" ] && [ -n "$(env_get "$ENV_FILE" PARTY_VAPID_SUBJECT)" ] \
  || die "$ENV_FILE 에 값이 비어 있다. 복구: 백업(*.bak-push-$TS)을 되돌린다"
log "완료. 다음: (1) 도로 파티 배포(또는 party-api 재생성) (2) 앱의 알림 설정에서 시험 알림. 개인 키를 잃으면 --rotate 로 다시 만든다."
