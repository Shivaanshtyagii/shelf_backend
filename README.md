# Shelf API

The marketplace is a Java 21 Spring Boot application with feature modules and PostgreSQL 17. Run the commands below from `backend`. The Next.js app has separate [setup instructions](../frontend/README.md).

## Local development

Install Java 21 and Maven, then start PostgreSQL and the local email inbox.

```sh
docker compose up -d
SHELF_SECURE_COOKIE=false mvn spring-boot:run
```

Spring applies the Flyway migrations on startup. The API uses port 8080. Open the Mailpit inbox at http://localhost:8025 to read verification and password-reset links sent by the local app.

Create and verify an account before granting it moderator access in the local database. Substitute the address for that account in the following query.

```sql
update accounts.member
set role = 'MODERATOR'
where email = 'moderator@example.com' and verified;
```

Moderators can review seller applications, listing revisions, and directory and affiliation requests. Add colleges through the directory workflow or seed the pilot's institution directory in PostgreSQL. A user can verify an affiliation with an account email whose verified domain exactly matches the configured college domain, or submit a request for review.

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

Feature packages expose their cross-module contracts at the package root and keep their implementations in `internal`. A feature accesses another feature's data through its public contract. Spring Modulith verifies these import boundaries and dependency cycles. Each feature owns its PostgreSQL schema.

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
SHELF_PILOT_HTTP_TEST=true SHELF_DB_URL=jdbc:postgresql://localhost:5432/shelf_test mvn test
```

The HTTP tests require an existing disposable PostgreSQL database and populate it with test fixtures. They exercise the real HTTP API, session cookies, migrations, and publishing and campus gates. SMTP is mocked for the tests. Exercise upload and download with your R2 and ClamAV setup before opening the pilot. Digital checkout, refunds, and payouts are disabled until a provider is integrated.
