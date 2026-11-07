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
| PDF renderer | http://localhost:3000 | Gotenberg (headless Chromium), used by PDF export |

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

Test split and coverage:

| Plugin | Runs | Phase |
|---|---|---|
| Surefire | unit tests `*Test` (no Spring context, no containers) | `test` |
| Failsafe | integration tests `*IT` (Spring context, SQL Server and the PDF renderer via Testcontainers, MCP over HTTP) | `integration-test` / `verify` |

JaCoCo measures both runs separately and merges them. HTML/XML/CSV reports go to `target/site/jacoco-unit`, `target/site/jacoco-it` and `target/site/jacoco` (merged). Skip measurement with `-Djacoco.skip=true`.

## Observability

Actuator runs on a separate **management port, 8081** (`MANAGEMENT_PORT`). Keep that port cluster-internal: do not route it through the ingress. The application port (8080) does not serve `/actuator` at all. On the management port these endpoints are open without authentication, for Kubernetes and Prometheus:

| Endpoint | Purpose |
|---|---|
| `/actuator/health/liveness` | Liveness probe. It does not depend on the database: restarting would not fix a database outage. |
| `/actuator/health/readiness` | Readiness probe, including the database connection. |
| `/actuator/health`, `/actuator/info` | Overall status (no component details for anonymous callers). |
| `/actuator/prometheus` | Prometheus scrape endpoint (JVM, HTTP server with histograms, Hikari pools, …), tagged `application="reqlith"`. |

No other actuator endpoint is exposed.

```yaml
# Kubernetes container spec
livenessProbe:
  httpGet: { path: /actuator/health/liveness, port: 8081 }
  initialDelaySeconds: 30
readinessProbe:
  httpGet: { path: /actuator/health/readiness, port: 8081 }
# Pod annotations for annotation-based Prometheus scraping
prometheus.io/scrape: "true"
prometheus.io/path: /actuator/prometheus
prometheus.io/port: "8081"
```

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

PDF export renders the PRD Markdown with the same renderer as the UI and sends the HTML to a separate rendering service, [Gotenberg](https://gotenberg.dev). Gotenberg prints it with headless Chromium, so Mermaid diagrams are drawn as in the browser, while the application image needs no browser.

- **No network for rendered pages:** fonts are embedded, and `mermaid.min.js` is uploaded with the page. The renderer runs with `--chromium-allow-list=^file:///tmp/.*`, so a page can only read its own uploaded files.
- **Configuration:** `REQLITH_PDF_URL` (default `http://localhost:3000`). In Kubernetes, run the renderer as its own Deployment and Service, without an ingress.
- **Caching:** PDFs of closed versions are cached in `export_cache`, keyed by content hash, version, status and export options. Only the first export needs the renderer.

## Repository layout

| Path | Contents |
|---|---|
| `src/main/kotlin/io/reqlith` | Application code, by layer (see below) |
| `src/main/resources` | Templates, static assets, Flyway migrations, configuration |
| `src/main/docker/Dockerfile` | Application image: Alpine + JRE 25, built from Maven output (build context: repository root, see `.dockerignore`) |
| `src/main/dev/compose.yaml` | Local environment for Docker compose |
| `src/main/dev/keycloak`, `src/main/dev/mssql` | Keycloak realm with test users, database init script |
| `src/main/dev/scripts` | `podman-dev.sh` (environment without compose), `test.sh`, `build-image.sh`, `update-vendor.sh` |

Packages under `io.reqlith`:

```
├── config        Spring configuration: security (web UI and /mcp), properties, MCP read-only database
├── controller    MVC controllers, MCP tools, view helpers (formats, project page support)
├── service       business logic: projects, elements, roles, reviews, versions, snapshots, diff, export, markdown
├── repository    Spring Data repositories and JDBC access (key counter, export cache, MCP read model and access log)
├── model
│   ├── entity    JPA entities and their enums
│   └── dto       documents (snapshots), forms, view models, MCP results, principals
└── exception     business exceptions and the error page handler
```

Tests mirror this layout (`*Test` unit tests, `*IT` integration tests). Shared test setup lives in `support`.

The image has no builder stage. Maven builds everything into `target/`, and the Dockerfile only copies the results onto Alpine with the JRE installed from Alpine's packages (`openjdk25-jre-headless`; the build fails if it is not Java 25):

```bash
src/main/dev/scripts/build-image.sh           # = mvnd -DskipTests package && docker/podman build -f src/main/docker/Dockerfile .
```

| Layer | Source | Changes |
|---|---|---|
| Alpine + JRE 25 | `apk add openjdk25-jre-headless` | rarely |
| `/opt/reqlith/lib` | `target/docker/lib`: runtime dependencies (`dependency:copy-dependencies`, without devtools) | when dependencies change |
| `/opt/reqlith/classes` | `target/classes`: compiled Kotlin, templates, static assets, migrations | with every code change (about 5 MB) |

The app starts with `java -cp classes:lib/* io.reqlith.ReqlithApplicationKt`. The image is about 350 MB, compared with 1.3 GB when Chromium was part of it. Building requires JDK 25 or newer, which the Maven enforcer plugin checks.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `REQLITH_DB_URL` / `REQLITH_DB_USER` / `REQLITH_DB_PASSWORD` | local MSSQL | Main datasource (schema owner, Flyway) |
| `REQLITH_IDP_ISSUER` | `http://localhost:8180/realms/reqlith` | Token issuer, also the URL browsers are redirected to |
| `REQLITH_IDP_INTERNAL_URL` | issuer | How the server reaches the IDP, when that differs (containers) |
| `REQLITH_IDP_CLIENT_ID` / `REQLITH_IDP_CLIENT_SECRET` | `reqlith-app` / dev secret | OIDC client |
| `REQLITH_BASE_URL` | `http://localhost:8080` | Public URL for copyable links and the MCP config |
| `REQLITH_PDF_URL` | `http://localhost:3000` | PDF rendering service (Gotenberg) |
| `REQLITH_MCP_DB_URL` / `REQLITH_MCP_DB_USER` / `REQLITH_MCP_DB_PASSWORD` | main URL / `reqlith_mcp` / dev password | Read-only connection used by the MCP server |
| `MANAGEMENT_PORT` | `8081` | Actuator port (probes, Prometheus) |

The IDP endpoints are configured explicitly rather than discovered, so the app starts even when the IDP is down. The ID token issuer is still validated against `REQLITH_IDP_ISSUER`. IDP realm roles are read from the `roles` claim, and `reqlith-admin` grants workspace management.

## Design notes

- **Working copy and snapshots.** Authors edit a mutable working copy. Submitting for review freezes the whole project as a canonical JSON snapshot (`project_version`). Approved versions get gapless numbers `v1, v2, …`, and candidates are labelled `vN-rcK`. A database trigger (`V2__version_immutability.sql`) refuses any change to an approved version and any change to any snapshot.
- Element keys (`BR-3`, `FR-12`, …) are never reused, and links from JIRA/ADO should use `/p/{key}/v/{n}`, which never changes.
