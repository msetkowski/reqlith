# Reqlith

Requirement engineering tool. It collects BABOK-style requirements into a project, reviews the whole project and freezes each approved state as an immutable, numbered version that a development AI agent can consume (PRD export, read-only MCP server).

Stack: Spring Boot 4.1 · Kotlin · Spring MVC + Thymeleaf + HTMX · MS SQL Server + Flyway · OAuth 2 / OIDC (Keycloak for local dev).
All JavaScript and fonts are vendored in `src/main/resources/static/vendor` (see `scripts/update-vendor.sh`), so no CDN is needed.

## Prerequisites

- JDK 25
- [mvnd](https://github.com/apache/maven-mvnd) (`sdk install mvnd`). The Maven wrapper (`./mvnw`) works too.
- Docker with compose, **or** Podman

## Local environment

Start SQL Server and Keycloak:

```bash
docker compose up -d          # or: scripts/podman-dev.sh up
```

| Service  | Address                 | Credentials                                   |
|----------|-------------------------|-----------------------------------------------|
| MSSQL    | `localhost:1433`        | db `reqlith`, `reqlith_app` / `Reqlith_dev_app_1` |
| Keycloak | http://localhost:8180   | admin console `admin` / `admin`, realm `reqlith` |

Test users (password `password` for all): `owner1` (also `reqlith-admin`), `owner2`, `contrib1`, `reviewer1`, `reviewer2`, `viewer1`.

Run the app on the host:

```bash
mvnd spring-boot:run -Dspring-boot.run.profiles=local
# open http://localhost:8080
```

To run everything in containers instead, use `docker compose --profile full up -d --build` or `scripts/podman-dev.sh up --with-app`.
Stop the environment with `scripts/podman-dev.sh down`. Add `--volumes` to also drop the database.

## Tests

```bash
mvnd verify
```

Integration tests (`*IT`) start SQL Server through Testcontainers. With Podman:

```bash
systemctl --user start podman.socket
export DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock TESTCONTAINERS_RYUK_DISABLED=true
```

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `REQLITH_DB_URL` / `REQLITH_DB_USER` / `REQLITH_DB_PASSWORD` | local MSSQL | Main datasource (schema owner, Flyway) |
| `REQLITH_IDP_ISSUER` | `http://localhost:8180/realms/reqlith` | Token issuer, also the URL browsers are redirected to |
| `REQLITH_IDP_INTERNAL_URL` | issuer | How the server reaches the IDP, when that differs (containers) |
| `REQLITH_IDP_CLIENT_ID` / `REQLITH_IDP_CLIENT_SECRET` | `reqlith-app` / dev secret | OIDC client |
| `REQLITH_BASE_URL` | `http://localhost:8080` | Public URL for copyable links and the MCP config |

The IDP endpoints are configured explicitly rather than discovered, so the app starts even when the IDP is down. The ID token issuer is still validated against `REQLITH_IDP_ISSUER`. IDP realm roles are read from the `roles` claim, and `reqlith-admin` grants workspace management.

## Design notes

- **Working copy and snapshots.** Authors edit a mutable working copy. Submitting for review freezes the whole project as a canonical JSON snapshot (`project_version`). Approved versions get gapless numbers `v1, v2, …`, and candidates are labelled `vN-rcK`. A database trigger (`V2__version_immutability.sql`) refuses any change to an approved version and any change to any snapshot.
- Element keys (`BR-3`, `FR-12`, …) are never reused, and links from JIRA/ADO should use `/p/{key}/v/{n}`, which never changes.
