#!/usr/bin/env bash
# Runs everything locally with Podman: tests, image build, the full stack in a pod, then smoke checks
# against the running containers (sign-in through Keycloak, probes, metrics, MCP, PDF rendering).
#
#   src/main/dev/scripts/podman-test.sh              tests + image + stack + smoke checks
#   src/main/dev/scripts/podman-test.sh --skip-tests image + stack + smoke checks
#   src/main/dev/scripts/podman-test.sh --fresh      start from an empty database (drops the volume)
#   src/main/dev/scripts/podman-test.sh --down       stop the stack after the checks
#
# The stack keeps running afterwards (unless --down): http://localhost:8080, owner1 / password.
set -euo pipefail

SCRIPTS="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SCRIPTS/../../../.." && pwd)"
APP=http://localhost:8080
MANAGEMENT=http://localhost:8081
KEYCLOAK=http://localhost:8180
RENDERER=http://localhost:3000

run_tests=true; fresh=false; down=false
for arg in "$@"; do
  case "$arg" in
    --skip-tests) run_tests=false ;;
    --fresh) fresh=true ;;
    --down) down=true ;;
    *) echo "Unknown option $arg" >&2; sed -n '2,10p' "$0" >&2; exit 2 ;;
  esac
done

step() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
passed=0; failed=0
check() { # check <description> <command...>
  local name="$1"; shift
  if "$@" >/dev/null 2>&1; then printf '  \033[32m✓\033[0m %s\n' "$name"; passed=$((passed + 1))
  else printf '  \033[31m✗\033[0m %s\n' "$name"; failed=$((failed + 1)); fi
}
wait_for() { # wait_for <description> <url> <seconds>
  printf '  waiting for %s' "$1"
  for _ in $(seq 1 "$3"); do
    if curl -sf -o /dev/null "$2"; then echo " ok"; return 0; fi
    printf '.'; sleep 1
  done
  echo " timed out"; return 1
}

if $run_tests; then
  step "Unit and integration tests"
  "$SCRIPTS/test.sh" clean verify
fi

step "Image and stack (MSSQL, Keycloak, PDF renderer, Reqlith)"
if $fresh; then "$SCRIPTS/podman-dev.sh" down --volumes; else "$SCRIPTS/podman-dev.sh" down; fi
"$SCRIPTS/podman-dev.sh" up --with-app

step "Waiting for the services"
wait_for "Keycloak" "$KEYCLOAK/realms/reqlith" 180
wait_for "PDF renderer" "$RENDERER/health" 120
wait_for "Reqlith readiness" "$MANAGEMENT/actuator/health/readiness" 180

step "Smoke checks"
WORK="$(mktemp -d)"; trap 'rm -rf "$WORK"' EXIT
JAR="$WORK/cookies"

check "liveness probe is UP" sh -c "curl -sf $MANAGEMENT/actuator/health/liveness | grep -q UP"
check "readiness probe (incl. database) is UP" sh -c "curl -sf $MANAGEMENT/actuator/health/readiness | grep -q UP"
check "Prometheus metrics are exposed" sh -c "curl -sf $MANAGEMENT/actuator/prometheus | grep -q 'application=\"reqlith\"'"
check "application port does not serve actuator" sh -c "[ \"\$(curl -s -o /dev/null -w '%{http_code}' $APP/actuator/prometheus)\" != 200 ]"
check "anonymous users are sent to the identity provider" \
  sh -c "curl -s -o /dev/null -w '%{redirect_url}' $APP/ | grep -q '/oauth2/authorization/idp'"

# Sign in as owner1 through the Keycloak login form.
login_form=$(curl -s -L -c "$JAR" -b "$JAR" "$APP/" | grep -oE 'action="[^"]+"' | head -1 | sed -e 's/action="//' -e 's/"$//' -e 's/&amp;/\&/g')
curl -s -L -c "$JAR" -b "$JAR" -d username=owner1 -d password=password "$login_form" -o "$WORK/home.html"
check "owner1 signs in through Keycloak and sees the projects" grep -q 'Specifications you own' "$WORK/home.html"
check "requests get a request id" sh -c "curl -s -D - -o /dev/null -b $JAR $APP/ | grep -qi '^x-request-id:'"

check "MCP rejects anonymous calls and points to the resource metadata" \
  sh -c "curl -s -D - -o /dev/null -X POST -H 'Content-Type: application/json' -d '{}' $APP/mcp | grep -qi 'resource_metadata'"
check "OAuth protected resource metadata is published" sh -c "curl -sf $APP/.well-known/oauth-protected-resource | grep -q authorization_servers"

project=$(grep -oE 'href="/p/PRJ-[A-Z0-9]+-[0-9]+"' "$WORK/home.html" | head -1 | sed -E 's#href="/p/(.*)"#\1#' || true)
if [[ -n "$project" ]]; then
  curl -s -b "$JAR" -o "$WORK/prd.pdf" "$APP/p/$project/draft/prd.pdf"
  check "PDF export of $project is rendered by the renderer" sh -c "head -c 4 $WORK/prd.pdf | grep -q '%PDF'"
  check "Markdown export of $project" sh -c "curl -sf -b $JAR $APP/p/$project/draft/prd.md | grep -q '^# '"
else
  echo "  - no project yet: create one to include the export checks"
fi

step "Result"
echo "  $passed passed, $failed failed"
if $down; then
  "$SCRIPTS/podman-dev.sh" down
else
  echo "  Stack is running: $APP (owner1 / password), actuator $MANAGEMENT/actuator, Keycloak $KEYCLOAK"
  echo "  Stop it with: $SCRIPTS/podman-dev.sh down"
fi
[[ $failed -eq 0 ]]
