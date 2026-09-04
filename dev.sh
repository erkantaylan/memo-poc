#!/usr/bin/env bash
# One-command dev loop: serve the library, open the tunnel, build, install, launch.
#
#   ./dev.sh            build, install and launch
#   ./dev.sh --logs     ...then tail the app's logcat
#
# The library server runs in the background and is reused across runs.
set -euo pipefail

PORT=8090
APP_ID=com.erkantaylan.kitaplik.debug
LIBRARY_DIR="$(cd "$(dirname "$0")/../library" && pwd)"
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

# --- device -----------------------------------------------------------------
if ! adb devices | grep -q "device$"; then
  echo "No device connected. Trying to reconnect over Wi-Fi..."
  bash "$PROJECT_DIR/../env-setup/adb-wifi-connect.sh"
fi

# --- library server ---------------------------------------------------------
if curl -sf -o /dev/null --max-time 2 "http://localhost:$PORT/catalog.json"; then
  echo "==> Library server already up on :$PORT"
else
  echo "==> Starting library server on :$PORT ($LIBRARY_DIR)"
  (cd "$LIBRARY_DIR" && nohup python3 -m http.server "$PORT" --bind 127.0.0.1 \
      >/tmp/kitaplik-library-server.log 2>&1 &)
  for _ in $(seq 1 20); do
    curl -sf -o /dev/null --max-time 1 "http://localhost:$PORT/catalog.json" && break
    sleep 0.25
  done
  curl -sf -o /dev/null --max-time 2 "http://localhost:$PORT/catalog.json" \
    || { echo "Library server did not come up; see /tmp/kitaplik-library-server.log" >&2; exit 1; }
fi

# --- tunnel -----------------------------------------------------------------
adb reverse "tcp:$PORT" "tcp:$PORT" >/dev/null
echo "==> Tunnel: device localhost:$PORT -> workstation :$PORT"

# --- build, install, launch -------------------------------------------------
"$PROJECT_DIR/gradlew" -p "$PROJECT_DIR" :app:installDebug

adb shell am force-stop "$APP_ID"
adb shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
echo "==> Launched $APP_ID"

if [ "${1:-}" = "--logs" ]; then
  PID="$(adb shell pidof "$APP_ID" | tr -d '\r')"
  echo "==> Tailing logcat for pid $PID (Ctrl-C to stop)"
  adb logcat --pid="$PID"
fi
