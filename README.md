# Shelf API

The marketplace is a Java 21 Spring Boot application with feature modules and PostgreSQL on Neon. Run the commands below from `backend`. The Next.js app has separate [setup instructions](../frontend/README.md).

## Local development

Install Java 21, Maven, Node.js 22+, and the Neon CLI. In `backend/`, confirm that the Neon link points to `dev`. Run `neon deploy` if Auth needs provisioning, or `neon env pull` to refresh an existing setup. Then start the backend.

```sh
npm run dev:neon
```

The API uses port 8080. Spring uses the pooled Neon connection for app traffic and Flyway uses the direct connection for migrations. Neon Auth handles account emails.

Create and verify the account in Neon Auth, then sign in to Shelf to create its member profile. To grant moderator access, use the verified user's Neon Auth ID in the following query.

```sql
update accounts.member
set role = 'MODERATOR'
where neon_subject = 'VERIFIED_NEON_AUTH_USER_ID';
```

Moderators can review seller applications, listing revisions, and directory and affiliation requests. Add colleges through the directory workflow or seed the pilot's institution directory in PostgreSQL. A user can verify an affiliation with an account email whose verified domain exactly matches the configured college domain, or submit a request for review.

## Google sign-in and email

### Managed Neon sign-in on the development branch

Put that same branch's `NEON_AUTH_BASE_URL` and a server-only `NEON_AUTH_COOKIE_SECRET` (generate with `openssl rand -base64 32`) in `frontend/.env.local`. From `frontend/` run `npm run dev:neon` and open `http://localhost:3000`. When using a different frontend origin, set `SHELF_WEB_ORIGIN` in the frontend and trust the origin in Neon Auth. When using a different Spring port, set `SHELF_API_ORIGIN` in the frontend.

Neon manages sign-in and identity verification. Spring validates the branch's signed bearer tokens and provisions Shelf profiles by their Neon subject. Roles and campus and listing permissions remain Shelf-owned. If a Neon account email already belongs to a legacy Shelf member, sign-in will report a conflict until the accounts are explicitly linked. Configure Google, verification codes, password resets, and production email delivery through Neon Auth.

For deployment, configure Spring's `neon` profile, its `SHELF_DB_*` settings, direct `SPRING_FLYWAY_*` credentials, and the branch's Auth issuer and JWKS variables. Set `NEXT_PUBLIC_SHELF_NEON_AUTH=true` at frontend build and runtime. Set the trusted HTTPS origin and a backend origin reachable from the Next.js deployment.

### Spring-managed sign-in

For the legacy Spring-managed sign-in, set `SHELF_DB_URL`, `SHELF_DB_USER`, and `SHELF_DB_PASSWORD` for an independently provisioned PostgreSQL database. Start Spring with `SHELF_SECURE_COOKIE=false mvn spring-boot:run` on local HTTP. On HTTPS deployments keep secure cookies enabled. For email delivery set `SHELF_SMTP_HOST`, `SHELF_SMTP_PORT`, `SHELF_SMTP_USER`, `SHELF_SMTP_PASSWORD`, `SHELF_SMTP_AUTH=true`, `SHELF_SMTP_STARTTLS=true`, and a provider-authorized `SHELF_MAIL_FROM`. Set `SHELF_WEB_ORIGIN` to the public frontend origin for links.

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

For a Neon deployment, route browser API requests to Next.js and set its upstream Spring origin. For a legacy Spring-managed deployment, configure database and SMTP settings in [application.yml](src/main/resources/application.yml) and keep secure session cookies enabled on HTTPS. Rate-limit account and upload endpoints at the public reverse proxy.

## Checks

```sh
mvn test
SHELF_PILOT_HTTP_TEST=true mvn test
```

Before enabling HTTP tests, export `SHELF_DB_URL`, `SHELF_DB_USER`, and `SHELF_DB_PASSWORD` for an existing disposable PostgreSQL database. The tests populate it with fixtures and exercise the real HTTP API, session cookies, migrations, and publishing and campus gates. SMTP is mocked. Exercise upload and download with your R2 and ClamAV setup before opening the pilot. Digital checkout, refunds, and payouts are disabled until a provider is integrated.
