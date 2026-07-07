#!/usr/bin/env bash
# shellcheck shell=bash
# Start Java (or any command) detached from the current shell session.

start_java_daemon() {
  local pidfile="$1"
  local logfile="$2"
  shift 2
  python3 "$ROOT/scripts/java-daemon.py" "$pidfile" "$logfile" -- "$@"
}

verify_daemon_listener() {
  local pidfile="$1"
  local port="$2"
  local label="$3"
  local logfile="$4"
  local pid

  sleep 2
  pid="$(tr -d '\n' < "$pidfile" 2>/dev/null || true)"
  if [[ -z "${pid:-}" ]] || ! kill -0 "$pid" 2>/dev/null; then
    echo "[flowfoundry] ERROR: $label exited shortly after start (pid=${pid:-none})"
    tail -40 "$logfile" 2>/dev/null || true
    exit 1
  fi
  if ! lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "[flowfoundry] ERROR: $label is not listening on :$port (pid=$pid)"
    tail -40 "$logfile" 2>/dev/null || true
    exit 1
  fi
  echo "[flowfoundry] verified $label pid=$pid listening on :$port"
}
