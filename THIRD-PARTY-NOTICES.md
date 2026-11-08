# Third-party notices

Reqlith is licensed under the [Apache License 2.0](LICENSE). It redistributes the following third-party
files in `src/main/resources/static/vendor` (refreshed by `src/main/dev/scripts/update-vendor.sh`), each
under its own license. The license texts are in [`src/main/resources/static/vendor/licenses`](src/main/resources/static/vendor/licenses).

| Component | Version | License | Files |
|---|---|---|---|
| [htmx](https://htmx.org) | 2.0.11 | 0BSD | `htmx.min.js` |
| [Mermaid](https://mermaid.js.org) | 11.17.2 | MIT | `mermaid.min.js` (bundles its own dependencies, such as D3 and DOMPurify, under their permissive licenses) |
| [IBM Plex Sans](https://github.com/IBM/plex), via [Fontsource](https://fontsource.org) | 5.3.0 | SIL Open Font License 1.1 | `fonts/ibm-plex-sans-*.woff2` |
| [IBM Plex Mono](https://github.com/IBM/plex), via [Fontsource](https://fontsource.org) | 5.3.0 | SIL Open Font License 1.1 | `fonts/ibm-plex-mono-*.woff2` |

Libraries resolved by Maven at build time (Spring, Kotlin, Jackson, commonmark-java, OWASP Java HTML Sanitizer,
java-diff-utils, kotlin-logging, Microsoft JDBC Driver for SQL Server, Flyway, …) are not stored in this repository.
They are included in the container image under their respective licenses.

The development environment pulls container images ([Keycloak](https://www.keycloak.org),
[Microsoft SQL Server](https://hub.docker.com/r/microsoft/mssql-server) under the Microsoft EULA, accepted with
`ACCEPT_EULA=Y`, and [Gotenberg](https://gotenberg.dev)), which are not part of this repository.

BABOK® is a registered trademark of the International Institute of Business Analysis (IIBA). Reqlith is not
affiliated with or endorsed by IIBA.
