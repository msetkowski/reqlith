#!/usr/bin/env bash
# Podman equivalent of compose.yaml, without podman-compose.
# All containers run in one pod and share localhost, so ports match the compose setup:
#   1433 MSSQL, 8180 Keycloak, 8080 app (only with --with-app; otherwise run the app on the host).
#
# Usage: src/main/dev/scripts/podman-dev.sh up [--with-app] | down [--volumes] | status | logs <container>
set -euo pipefail

DEV="$(cd "$(dirname "$0")/.." && pwd)"         # src/main/dev
ROOT="$(cd "$DEV/../../.." && pwd)"              # repository root
POD=reqlith
MSSQL_IMAGE=mcr.microsoft.com/mssql/server:2022-latest
KEYCLOAK_IMAGE=quay.io/keycloak/keycloak:26.8.2
APP_IMAGE=localhost/reqlith:dev
SA_PASSWORD="${MSSQL_SA_PASSWORD:-Reqlith_dev_SA_1}"
APP_DB_PASSWORD="${REQLITH_DB_PASSWORD:-Reqlith_dev_app_1}"
MCP_DB_PASSWORD="${REQLITH_MCP_DB_PASSWORD:-Reqlith_dev_mcp_1}"
SQLCMD=/opt/mssql-tools18/bin/sqlcmd

wait_for_mssql() {
  echo -n "Waiting for MSSQL"
  for _ in $(seq 1 60); do
    if podman exec "$POD-mssql" "$SQLCMD" -C -S localhost -U sa -P "$SA_PASSWORD" -Q 'select 1' -b -o /dev/null 2>/dev/null; then
      echo " ready"; return 0
    fi
    echo -n "."; sleep 2
  done
  echo; echo "MSSQL did not become ready" >&2; exit 1
}

up() {
  local with_app=false
  [[ "${1:-}" == "--with-app" ]] && with_app=true

  # Port 8080 is published only when the app runs in the pod; otherwise the app runs on the host.
  if podman pod exists "$POD"; then
    local has_app_port=false
    podman pod inspect "$POD" --format '{{json .InfraConfig.PortBindings}}' | grep -q '"8080/tcp"' && has_app_port=true
    if [[ "$with_app" != "$has_app_port" ]]; then
      echo "Pod $POD was created $($has_app_port && echo with || echo without) the app; run '$0 down' first." >&2
      exit 1
    fi
  else
    local ports=(-p 1433:1433 -p 8180:8180)
    $with_app && ports+=(-p 8080:8080)
    podman pod create --name "$POD" "${ports[@]}"
  fi
  podman volume exists "$POD-mssql-data" || podman volume create "$POD-mssql-data" >/dev/null

  if ! podman container exists "$POD-mssql"; then
    podman run -d --pod "$POD" --name "$POD-mssql" \
      -e ACCEPT_EULA=Y -e MSSQL_PID=Developer -e MSSQL_SA_PASSWORD="$SA_PASSWORD" \
      -v "$POD-mssql-data:/var/opt/mssql" \
      "$MSSQL_IMAGE" >/dev/null
  else
    podman start "$POD-mssql" >/dev/null
  fi

  if ! podman container exists "$POD-keycloak"; then
    podman run -d --pod "$POD" --name "$POD-keycloak" \
      -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
      -e KC_HOSTNAME=http://localhost:8180 -e KC_HTTP_PORT=8180 -e KC_HEALTH_ENABLED=true \
      -v "$DEV/keycloak/reqlith-realm.json:/opt/keycloak/data/import/reqlith-realm.json:ro,z" \
      "$KEYCLOAK_IMAGE" start-dev --import-realm >/dev/null
  else
    podman start "$POD-keycloak" >/dev/null
  fi

  wait_for_mssql
  podman cp "$DEV/mssql/init.sql" "$POD-mssql:/tmp/init.sql"
  podman exec -e APP_PASSWORD="$APP_DB_PASSWORD" -e MCP_PASSWORD="$MCP_DB_PASSWORD" "$POD-mssql" \
    "$SQLCMD" -C -S localhost -U sa -P "$SA_PASSWORD" -b -i /tmp/init.sql

  if $with_app; then
    podman build -t "$APP_IMAGE" -f "$ROOT/src/main/docker/Dockerfile" "$ROOT"
    podman rm -f "$POD-app" >/dev/null 2>&1 || true
    podman run -d --pod "$POD" --name "$POD-app" \
      -e REQLITH_DB_PASSWORD="$APP_DB_PASSWORD" -e REQLITH_MCP_DB_PASSWORD="$MCP_DB_PASSWORD" \
      "$APP_IMAGE" >/dev/null
  fi

  echo "MSSQL     localhost:1433 (db reqlith, user reqlith_app)"
  echo "Keycloak  http://localhost:8180 (admin/admin, realm reqlith, users owner1..viewer1 / password)"
  $with_app && echo "App       http://localhost:8080"
  return 0
}

down() {
  podman pod rm -f "$POD" >/dev/null 2>&1 || true
  if [[ "${1:-}" == "--volumes" ]]; then
    podman volume rm -f "$POD-mssql-data" >/dev/null 2>&1 || true
  fi
  echo "Pod $POD removed"
}

case "${1:-}" in
  up) shift; up "$@" ;;
  down) shift; down "$@" ;;
  status) podman pod ps --filter "name=^$POD\$"; podman ps -a --pod --filter "pod=$POD" ;;
  logs) podman logs -f "$POD-${2:?container: mssql|keycloak|app}" ;;
  *) echo "Usage: $0 up [--with-app] | down [--volumes] | status | logs <mssql|keycloak|app>" >&2; exit 2 ;;
esac
