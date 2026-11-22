# Shelf API

The marketplace is a Java 21 Spring Boot application with feature modules and PostgreSQL 17. Run the commands below from `backend`. The Next.js app has separate [setup instructions](../frontend/README.md).

## Local development

Install Java 21 and Maven, then start PostgreSQL and the local email inbox.

```sh
docker compose up -d
SHELF_SECURE_COOKIE=false mvn spring-boot:run
```

Spring applies the Flyway migrations on startup. The API uses port 8080 and connects to the Docker database at 127.0.0.1:15432. Open the Mailpit inbox at http://localhost:8025 to read verification and password-reset links sent by the local app.

Create and verify an account before granting it moderator access in the local database. Substitute the address for that account in the following query.

```sql
update accounts.member
set role = 'MODERATOR'
where email = 'moderator@example.com' and verified;
```

Moderators can review seller applications, listing revisions, and directory and affiliation requests. Add colleges through the directory workflow or seed the pilot's institution directory in PostgreSQL. A user can verify an affiliation with an account email whose verified domain exactly matches the configured college domain, or submit a request for review.

## Google sign-in and email

For local password sign-in, open the site consistently at `http://localhost:3000` and start Spring with `SHELF_SECURE_COOKIE=false`. On HTTPS deployments keep the secure cookie enabled. Local verification and password-reset messages go to the [Mailpit inbox](http://localhost:8025). For external delivery set `SHELF_SMTP_HOST`, `SHELF_SMTP_PORT`, `SHELF_SMTP_USER`, `SHELF_SMTP_PASSWORD`, `SHELF_SMTP_AUTH=true`, `SHELF_SMTP_STARTTLS=true` and a provider-authorized `SHELF_MAIL_FROM`. Set `SHELF_WEB_ORIGIN` to the public frontend origin for links.

To enable Google, create a **Web application** OAuth client in Google Cloud. Register `http://localhost:3000/api/login/oauth2/code/google` as the authorized redirect URI for development and the corresponding HTTPS frontend URI for production. Configure the consent screen and test users as applicable. Provide `SHELF_GOOGLE_CLIENT_ID`, `SHELF_GOOGLE_CLIENT_SECRET`, `SHELF_WEB_ORIGIN` (without a trailing slash) and enable the Spring profile `google` via `SPRING_PROFILES_ACTIVE`. Restart Spring. The frontend reads provider availability from the backend. Google identities are identified by their stable subject. Existing password accounts use their original sign-in method when emails collide.

## Module ownership

| Module | Responsibility |
| --- | --- |
| Accounts | Email challenges, profiles, roles, and JDBC-backed sessions |
| Institutions | College and course directory, requests, and affiliations |
| Assets | The R2 storage adapter, file validation, scanning, failed-upload cleanup, and quotas |
| Listings | Seller enrollment, listing lifecycle, and scoped discovery |
| Moderation | Review actions, decision history, reports, and appeals |
| Pickup | Requests, scheduling, and the database constraint that permits one reservation per item |
| Commerce | Read-only payment status surfaces and disabled transaction endpoints |
| Capabilities | Reports upload and payment availability through the Assets and Commerce module interfaces |

Feature packages expose cross-module contracts at the package root. Within each feature, `internal/controller` handles HTTP routes and request validation, `internal/service` handles business rules and transactions, `internal/persistence` owns SQL and row mapping, and `internal/model` contains the records used by those layers. Features with provider integrations or configuration use `internal/integration` and `internal/config` as needed. Next.js owns the rendered views.

Each feature owns its PostgreSQL schema. Spring Modulith verifies cross-feature imports and dependency cycles. Architecture tests also enforce that controllers go through services for database work and JDBC stays in repositories.

## Uploads

Local scan and PDF export work in the browser. To enable cloud attachments, provide the following environment variables to the Spring process.

| Variable | Value |
| --- | --- |
| `SHELF_ASSETS_ENABLED` | `true` after R2 and the scanner are ready |
| `SHELF_R2_ACCOUNT` | Cloudflare account ID |
| `SHELF_R2_BUCKET` | Private R2 Standard bucket |
| `SHELF_R2_KEY_ID` and `SHELF_R2_SECRET` | R2 credentials scoped to that bucket |
| `SHELF_CLAMAV_HOST` and `SHELF_CLAMAV_PORT` | Private ClamAV daemon endpoint, port 3310 by default |
| `SHELF_ACCOUNT_ASSET_BYTES` | Global allocation, 8,000,000,000 bytes by default |
| `SHELF_USER_ASSET_BYTES` | Per-account allocation, 100,000,000 bytes by default |

For this pilot, Spring receives a size-limited upload, scans the temporary file, validates its type, reserves quota in PostgreSQL, and writes the accepted file to R2. Campus photos are re-encoded to strip embedded metadata before storage. The scanner must support streams up to the 25 MB PDF limit. Files are private and receive two-minute signed links after an application authorization check. Unattached files and failed uploads are cleaned up by a scheduled task. Keep the R2 bucket private and use it exclusively for Shelf so the app's quota reflects storage usage.

For deployment, set the database, frontend origin, SMTP host, port, credentials, TLS, and sender through the `SHELF_*` settings in [application.yml](src/main/resources/application.yml). Keep secure session cookies enabled on HTTPS. Route `/api` to Spring at the public reverse proxy and rate-limit account and upload endpoints there.

## Checks

```sh
mvn test
SHELF_PILOT_HTTP_TEST=true SHELF_DB_URL=jdbc:postgresql://127.0.0.1:15432/shelf_test mvn test
```

The HTTP tests require an existing disposable PostgreSQL database and populate it with test fixtures. They exercise the real HTTP API, session cookies, migrations, and publishing and campus gates. SMTP is mocked for the tests. Exercise upload and download with your R2 and ClamAV setup before opening the pilot. Digital checkout, refunds, and payouts are disabled until a provider is integrated.
