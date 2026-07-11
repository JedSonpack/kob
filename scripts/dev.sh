#!/usr/bin/env bash

set -u
set -o pipefail

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)

RUNTIME_DIR=${RUNTIME_DIR:-"$REPO_ROOT/.runtime"}
PID_DIR="$RUNTIME_DIR/pids"
LOG_DIR="$RUNTIME_DIR/logs"

JAVA_HOME=${KOB_JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home}
MAVEN_BIN=${MAVEN_BIN:-/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn}
NODE_BIN_DIR=${NODE_BIN_DIR:-"$HOME/.nvm/versions/node/v20.20.2/bin"}
MYSQL_SERVER=${MYSQL_SERVER:-/usr/local/mysql/support-files/mysql.server}

MYSQL_PORT=${MYSQL_PORT:-3306}
BACKEND_PORT=${BACKEND_PORT:-3000}
MATCHING_PORT=${MATCHING_PORT:-3001}
BOT_PORT=${BOT_PORT:-3002}
WEB_PORT=${WEB_PORT:-8080}
START_TIMEOUT=${START_TIMEOUT:-60}
STOP_TIMEOUT=${STOP_TIMEOUT:-10}

SERVICE_NAMES=(backend matching bot web)
STARTED_SERVICES=()

usage() {
  cat <<'EOF'
Usage: ./scripts/dev.sh <command>

Commands:
  start                 Start MySQL when needed and all KOB services
  status                Show MySQL and KOB service status
  logs [service]        Follow all logs or one of: backend, matching, bot, web
  stop                  Stop KOB services started by this script
  restart               Stop and start all KOB services
EOF
}

error() {
  printf 'Error: %s\n' "$*" >&2
}

service_port() {
  case "$1" in
    backend) printf '%s\n' "$BACKEND_PORT" ;;
    matching) printf '%s\n' "$MATCHING_PORT" ;;
    bot) printf '%s\n' "$BOT_PORT" ;;
    web) printf '%s\n' "$WEB_PORT" ;;
    *) return 1 ;;
  esac
}

service_dir() {
  case "$1" in
    backend) printf '%s\n' "$REPO_ROOT/backendcloud/backend" ;;
    matching) printf '%s\n' "$REPO_ROOT/backendcloud/matchingsystem" ;;
    bot) printf '%s\n' "$REPO_ROOT/backendcloud/botrunningsystem" ;;
    web) printf '%s\n' "$REPO_ROOT/web" ;;
    *) return 1 ;;
  esac
}

pid_file() {
  printf '%s/%s.pid\n' "$PID_DIR" "$1"
}

log_file() {
  printf '%s/%s.log\n' "$LOG_DIR" "$1"
}

port_open() {
  lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

read_live_pid() {
  local name=$1
  local file pid
  file=$(pid_file "$name")
  test -f "$file" || return 1
  pid=$(sed -n '1p' "$file" 2>/dev/null)
  case "$pid" in
    ''|*[!0-9]*) rm -f "$file"; return 1 ;;
  esac
  if kill -0 "$pid" 2>/dev/null; then
    printf '%s\n' "$pid"
    return 0
  fi
  rm -f "$file"
  return 1
}

wait_for_port() {
  local port=$1
  local timeout=$2
  local elapsed=0
  while test "$elapsed" -lt "$timeout"; do
    port_open "$port" && return 0
    sleep 1
    elapsed=$((elapsed + 1))
  done
  return 1
}

check_start_dependencies() {
  local failed=0 dir
  for executable in "$JAVA_HOME/bin/java" "$MAVEN_BIN" "$NODE_BIN_DIR/node" "$NODE_BIN_DIR/npm" "$MYSQL_SERVER"; do
    if test ! -x "$executable"; then
      error "required executable not found: $executable"
      failed=1
    fi
  done
  command -v lsof >/dev/null 2>&1 || { error "required command not found: lsof"; failed=1; }
  for name in "${SERVICE_NAMES[@]}"; do
    dir=$(service_dir "$name")
    if test ! -d "$dir"; then
      error "service directory not found: $dir"
      failed=1
    fi
  done
  test "$failed" -eq 0
}

ensure_mysql() {
  if port_open "$MYSQL_PORT"; then
    printf 'MySQL is ready on port %s.\n' "$MYSQL_PORT"
    return 0
  fi

  printf 'MySQL is not running; starting it now (sudo may prompt for your password)...\n'
  if ! sudo "$MYSQL_SERVER" start; then
    error "failed to start MySQL"
    return 1
  fi
  if ! wait_for_port "$MYSQL_PORT" "$START_TIMEOUT"; then
    error "MySQL did not listen on port $MYSQL_PORT within ${START_TIMEOUT}s"
    return 1
  fi
  printf 'MySQL is ready on port %s.\n' "$MYSQL_PORT"
}

print_log_tail() {
  local name=$1
  local file
  file=$(log_file "$name")
  if test -f "$file"; then
    printf '\nLast 40 lines of %s:\n' "$file" >&2
    tail -n 40 "$file" >&2
  fi
}

start_service() {
  local name=$1
  local port dir file pid
  port=$(service_port "$name")
  dir=$(service_dir "$name")
  file=$(pid_file "$name")

  if pid=$(read_live_pid "$name"); then
    if port_open "$port"; then
      printf '%-9s already running (PID %s, port %s).\n' "$name" "$pid" "$port"
      return 0
    fi
    printf '%-9s is still starting (PID %s); waiting for port %s...\n' "$name" "$pid" "$port"
    if wait_for_port "$port" "$START_TIMEOUT"; then
      return 0
    fi
    error "$name process is alive but port $port is not ready"
    return 1
  fi

  if port_open "$port"; then
    error "port $port is occupied by a process not managed by this script"
    return 1
  fi

  mkdir -p "$PID_DIR" "$LOG_DIR"
  : > "$(log_file "$name")"
  printf 'Starting %-9s on port %s...\n' "$name" "$port"

  if test "$name" = web; then
    (
      cd "$dir" || exit 1
      exec nohup env PATH="$NODE_BIN_DIR:$PATH" "$NODE_BIN_DIR/npm" run serve
    ) >>"$(log_file "$name")" 2>&1 </dev/null &
  else
    (
      cd "$dir" || exit 1
      exec nohup env JAVA_HOME="$JAVA_HOME" "$MAVEN_BIN" spring-boot:run
    ) >>"$(log_file "$name")" 2>&1 </dev/null &
  fi
  pid=$!
  printf '%s\n' "$pid" > "$file"
  STARTED_SERVICES+=("$name")

  local elapsed=0
  while test "$elapsed" -lt "$START_TIMEOUT"; do
    if port_open "$port"; then
      printf '%-9s ready (PID %s, port %s).\n' "$name" "$pid" "$port"
      return 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then
      rm -f "$file"
      error "$name exited before port $port became ready"
      print_log_tail "$name"
      return 1
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done

  error "$name did not listen on port $port within ${START_TIMEOUT}s"
  print_log_tail "$name"
  return 1
}

collect_process_tree() {
  local parent=$1
  local child
  printf '%s\n' "$parent"
  if command -v pgrep >/dev/null 2>&1; then
    for child in $(pgrep -P "$parent" 2>/dev/null || true); do
      collect_process_tree "$child"
    done
  fi
}

stop_service() {
  local name=$1
  local file pid elapsed process_pids live_pid
  file=$(pid_file "$name")
  if ! pid=$(read_live_pid "$name"); then
    rm -f "$file"
    printf '%-9s not managed or already stopped.\n' "$name"
    return 0
  fi

  process_pids=$(collect_process_tree "$pid" | tr '\n' ' ')
  printf 'Stopping %-9s (PID %s)...\n' "$name" "$pid"
  for live_pid in $process_pids; do
    kill -TERM "$live_pid" 2>/dev/null || true
  done

  elapsed=0
  while test "$elapsed" -lt "$STOP_TIMEOUT"; do
    local any_alive=0
    for live_pid in $process_pids; do
      if kill -0 "$live_pid" 2>/dev/null; then
        any_alive=1
        break
      fi
    done
    test "$any_alive" -eq 0 && break
    sleep 1
    elapsed=$((elapsed + 1))
  done

  for live_pid in $process_pids; do
    if kill -0 "$live_pid" 2>/dev/null; then
      kill -KILL "$live_pid" 2>/dev/null || true
    fi
  done
  rm -f "$file"
}

rollback_started_services() {
  local index
  if test "${#STARTED_SERVICES[@]}" -eq 0; then
    return
  fi
  printf 'Rolling back services started by this command...\n' >&2
  for ((index=${#STARTED_SERVICES[@]}-1; index>=0; index--)); do
    stop_service "${STARTED_SERVICES[$index]}"
  done
}

start_all() {
  check_start_dependencies || return 1
  mkdir -p "$PID_DIR" "$LOG_DIR"
  ensure_mysql || return 1
  STARTED_SERVICES=()
  for name in "${SERVICE_NAMES[@]}"; do
    if ! start_service "$name"; then
      rollback_started_services
      return 1
    fi
  done
  printf '\nAll KOB services are ready.\n'
  printf 'Frontend: http://localhost:%s/\n' "$WEB_PORT"
  printf 'Logs:    %s\n' "$LOG_DIR"
}

stop_all() {
  local index
  for ((index=${#SERVICE_NAMES[@]}-1; index>=0; index--)); do
    stop_service "${SERVICE_NAMES[$index]}"
  done
  printf 'KOB application services stopped. MySQL was left running.\n'
}

status_all() {
  local name port pid
  if port_open "$MYSQL_PORT"; then
    printf '%-9s running (port %s)\n' mysql "$MYSQL_PORT"
  else
    printf '%-9s stopped (port %s)\n' mysql "$MYSQL_PORT"
  fi
  for name in "${SERVICE_NAMES[@]}"; do
    port=$(service_port "$name")
    if pid=$(read_live_pid "$name"); then
      if port_open "$port"; then
        printf '%-9s running (PID %s, port %s)\n' "$name" "$pid" "$port"
      else
        printf '%-9s starting or unhealthy (PID %s, port %s closed)\n' "$name" "$pid" "$port"
      fi
    elif port_open "$port"; then
      printf '%-9s unmanaged process on port %s\n' "$name" "$port"
    else
      printf '%-9s stopped (port %s)\n' "$name" "$port"
    fi
  done
}

follow_logs() {
  local requested=${1:-}
  local name file
  local files=()
  if test -n "$requested"; then
    service_port "$requested" >/dev/null 2>&1 || { error "unknown service: $requested"; return 1; }
    file=$(log_file "$requested")
    test -f "$file" || { error "log file does not exist: $file"; return 1; }
    tail -n 100 -f "$file"
    return
  fi
  for name in "${SERVICE_NAMES[@]}"; do
    file=$(log_file "$name")
    test -f "$file" && files+=("$file")
  done
  test "${#files[@]}" -gt 0 || { error "no service logs exist under $LOG_DIR"; return 1; }
  tail -n 100 -f "${files[@]}"
}

command=${1:-}
case "$command" in
  start) start_all ;;
  status) status_all ;;
  logs) follow_logs "${2:-}" ;;
  stop) stop_all ;;
  restart) stop_all && start_all ;;
  '') usage >&2; exit 1 ;;
  *) error "unknown command: $command"; usage >&2; exit 1 ;;
esac
