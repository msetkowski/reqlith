#!/usr/bin/env bash
# Runs the build with tests (default: verify) and makes sure no test container is left behind.
#
#   src/main/dev/scripts/test.sh                 # mvnd verify (falls back to ./mvnw)
#   src/main/dev/scripts/test.sh test -Dtest=X   # any Maven arguments
#
# With Podman, the Docker-compatible socket is started if needed and Testcontainers' Ryuk reaper is
# configured for rootless Podman, so containers are removed even if the test JVM crashes. On exit
# (including Ctrl+C) any container labelled io.reqlith.test is removed as a last resort.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
LABEL=io.reqlith.test

if [[ -z "${DOCKER_HOST:-}" ]] && ! command -v docker >/dev/null && command -v podman >/dev/null; then
  SOCKET="/run/user/$(id -u)/podman/podman.sock"
  if [[ ! -S "$SOCKET" ]]; then
    echo "Starting the Podman API socket (systemctl --user start podman.socket)"
    systemctl --user start podman.socket
  fi
  export DOCKER_HOST="unix://$SOCKET"
  export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="$SOCKET"
  export TESTCONTAINERS_RYUK_CONTAINER_PRIVILEGED=true
fi
ENGINE=$(command -v docker || command -v podman)

cleanup() {
  local leftovers
  leftovers=$("$ENGINE" ps -aq --filter "label=$LABEL=true" 2>/dev/null)
  if [[ -n "$leftovers" ]]; then
    echo "Removing leftover test containers: $(echo $leftovers)"
    "$ENGINE" rm -f $leftovers >/dev/null
  fi
}
trap cleanup EXIT
trap 'exit 130' INT TERM

cleanup # containers from an earlier, killed run

MAVEN=$(command -v mvnd || echo "$ROOT/mvnw")
cd "$ROOT"
"$MAVEN" -B "${@:-verify}"
