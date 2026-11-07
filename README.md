# Reqlith

Requirement engineering tool. It collects BABOK-style requirements into a project, reviews the whole project and freezes each approved state as an immutable, numbered version that a development AI agent can consume (PRD export, read-only MCP server).

Stack: Spring Boot 4.1 · Kotlin · Spring MVC + Thymeleaf + HTMX · MS SQL Server + Flyway · OAuth 2 / OIDC (Keycloak for local dev).
All JavaScript and fonts are vendored in `src/main/resources/static/vendor` (see `src/main/dev/scripts/update-vendor.sh`), so no CDN is needed.

## Prerequisites

- JDK 25
- [mvnd](https://github.com/apache/maven-mvnd) (`sdk install mvnd`). The Maven wrapper (`./mvnw`) works too.
- Docker with compose, **or** Podman

## Local environment

Start SQL Server and Keycloak:

```bash
docker compose -f src/main/dev/compose.yaml up -d    # or: src/main/dev/scripts/podman-dev.sh up
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

To run everything in containers instead, use `docker compose -f src/main/dev/compose.yaml --profile full up -d --build` or `src/main/dev/scripts/podman-dev.sh up --with-app`.
Stop the environment with `src/main/dev/scripts/podman-dev.sh down`. Add `--volumes` to also drop the database.

## Tests

```bash
src/main/dev/scripts/test.sh            # = mvnd verify, plus container setup and cleanup
src/main/dev/scripts/test.sh test       # any Maven arguments
```

Integration tests (`*IT`) start SQL Server through Testcontainers. The script:

- With Podman, starts the user API socket if needed and sets `DOCKER_HOST`. It also configures Testcontainers' Ryuk reaper for rootless Podman (`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`, `TESTCONTAINERS_RYUK_CONTAINER_PRIVILEGED`), so containers are removed even when the test JVM crashes or is killed.
- On exit, including Ctrl+C, removes any container still labelled `io.reqlith.test`.
- Failsafe kills a test JVM that hangs for more than 20 minutes.

Running `mvnd verify` directly also works when Docker is available, or when the variables above are exported.

## MCP server for agents

`/mcp` is a read-only [Model Context Protocol](https://modelcontextprotocol.io) server (streamable HTTP) for agents that assess requirements, suggest improvements or build a backlog (e.g. in ADO) with their own tools.

- **Tools:** `list_projects`, `get_prd`, `search_requirements`, `get_requirement`, `get_traceability`, `list_versions`, `diff_versions`, `get_review_status`, `get_external_links`, `list_roles`. They return the **latest approved version** unless `version` is given (`"2"`, `"3-rc1"` or `"draft"`), so agents never build on unreviewed work by accident.
- **Authentication:** either a personal access token (`rql_…`, created under *Account → Access tokens for agents*, stored hashed) or an OAuth 2.1 access token from the IDP. Unauthenticated requests get `401` with `WWW-Authenticate: Bearer resource_metadata=…` pointing to `/.well-known/oauth-protected-resource`. The local realm has a public client `reqlith-mcp` (authorization code + PKCE, device flow).
- **Read-only by construction:** tools read through a separate connection pool logged in as `reqlith_mcp`, which only has `db_datareader` (configure it with `REQLITH_MCP_DB_URL`, `REQLITH_MCP_DB_USER` and `REQLITH_MCP_DB_PASSWORD`).
- **Access log:** every call is logged with client, user, token and tool, and shown on the project's *Export, links & MCP* page and on the token page.

Client configuration (Claude Code, Claude Desktop, …):

```json
{ "mcpServers": { "reqlith": { "type": "http", "url": "http://localhost:8080/mcp",
                                "headers": { "Authorization": "Bearer rql_…" } } } }
```

## PDF export

PDF export renders the PRD Markdown with the same renderer as the UI and prints it with headless Chromium (Playwright). Mermaid diagrams are therefore drawn as in the browser. The page has no network access: fonts are embedded and mermaid.js is injected from the vendored copy.

- **Running on the host:** Playwright downloads Chromium (about 120 MB, into `~/.cache/ms-playwright`) the first time a PDF is exported. To fetch it in advance, run
  `java -cp "$(./mvnw -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout)" com.microsoft.playwright.CLI install chromium`.
- **Container image:** Chromium is installed at build time (`PLAYWRIGHT_BROWSERS_PATH=/opt/ms-playwright`), and runtime downloads are disabled.
- **Caching:** PDFs of closed versions are cached in `export_cache`, keyed by content hash, version, status and export options.

## Repository layout

| Path | Contents |
|---|---|
| `src/main/kotlin`, `src/main/resources` | Application (templates, static assets, Flyway migrations) |
| `src/main/docker/Dockerfile` | Container image (build context: repository root, see `.dockerignore`) |
| `src/main/dev/compose.yaml` | Local environment for Docker compose |
| `src/main/dev/keycloak`, `src/main/dev/mssql` | Keycloak realm with test users, database init script |
| `src/main/dev/scripts` | `podman-dev.sh` (environment without compose), `test.sh`, `update-vendor.sh` |

Build the image with `docker build -f src/main/docker/Dockerfile .` (or `podman build`).

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
