#!/usr/bin/env bash

set -u

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
DEV_SCRIPT="$REPO_ROOT/scripts/dev.sh"
TEST_ROOT=$(mktemp -d "${TMPDIR:-/tmp}/kob-dev-test.XXXXXX")
BACKGROUND_PIDS=""

cleanup() {
  for pid in $BACKGROUND_PIDS; do
    kill "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
  done
  rm -rf "$TEST_ROOT"
}
trap cleanup EXIT INT TERM

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

expect_failure() {
  "$@" >/dev/null 2>&1 && fail "expected command to fail: $*"
}

expect_success() {
  "$@" >/dev/null 2>&1 || fail "expected command to succeed: $*"
}

test -x "$DEV_SCRIPT" || fail "$DEV_SCRIPT does not exist or is not executable"

expect_failure "$DEV_SCRIPT"
expect_failure "$DEV_SCRIPT" unknown
expect_failure env RUNTIME_DIR="$TEST_ROOT/runtime" "$DEV_SCRIPT" logs unknown

mkdir -p "$TEST_ROOT/runtime/pids"
printf '999999\n' > "$TEST_ROOT/runtime/pids/backend.pid"
env RUNTIME_DIR="$TEST_ROOT/runtime" "$DEV_SCRIPT" status >/dev/null 2>&1 || true
test ! -e "$TEST_ROOT/runtime/pids/backend.pid" || fail "status did not remove a stale PID file"

sleep 30 &
untracked_pid=$!
BACKGROUND_PIDS="$BACKGROUND_PIDS $untracked_pid"
expect_success env RUNTIME_DIR="$TEST_ROOT/empty-runtime" STOP_TIMEOUT=1 "$DEV_SCRIPT" stop
kill -0 "$untracked_pid" 2>/dev/null || fail "stop terminated an untracked process"

port=$(python3 - <<'PY'
import socket
s = socket.socket()
s.bind(('127.0.0.1', 0))
print(s.getsockname()[1])
s.close()
PY
)
python3 -m http.server "$port" --bind 127.0.0.1 >"$TEST_ROOT/unknown-port.log" 2>&1 &
port_pid=$!
BACKGROUND_PIDS="$BACKGROUND_PIDS $port_pid"
for _ in 1 2 3 4 5; do
  python3 - "$port" <<'PY' && break
import socket, sys
s = socket.socket()
s.settimeout(0.2)
try:
    s.connect(('127.0.0.1', int(sys.argv[1])))
except OSError:
    raise SystemExit(1)
finally:
    s.close()
PY
  sleep 0.2
done

mkdir -p "$TEST_ROOT/no-lsof"
printf '#!/usr/bin/env bash\nexit 1\n' > "$TEST_ROOT/no-lsof/lsof"
chmod +x "$TEST_ROOT/no-lsof/lsof"
mysql_status=$(env PATH="$TEST_ROOT/no-lsof:$PATH" MYSQL_PORT="$port" RUNTIME_DIR="$TEST_ROOT/status-runtime" "$DEV_SCRIPT" status)
printf '%s\n' "$mysql_status" | grep -q 'mysql.*running' || fail "status depends on lsof visibility instead of TCP reachability"

mkdir -p "$TEST_ROOT/java/bin" "$TEST_ROOT/node"
ln -s /usr/bin/true "$TEST_ROOT/java/bin/java"
ln -s /usr/bin/true "$TEST_ROOT/node/node"
ln -s /usr/bin/true "$TEST_ROOT/node/npm"

expect_failure env \
  RUNTIME_DIR="$TEST_ROOT/collision-runtime" \
  KOB_JAVA_HOME="$TEST_ROOT/java" \
  MAVEN_BIN=/usr/bin/true \
  NODE_BIN_DIR="$TEST_ROOT/node" \
  MYSQL_SERVER=/usr/bin/true \
  MYSQL_PORT="$port" \
  BACKEND_PORT="$port" \
  "$DEV_SCRIPT" start
kill -0 "$port_pid" 2>/dev/null || fail "start terminated the process occupying an unknown port"

printf 'PASS: scripts/dev.sh behavior tests\n'
