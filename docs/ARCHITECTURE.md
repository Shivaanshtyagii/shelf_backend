# Architecture

Shelf is one Spring Boot process backed by PostgreSQL. Follow an HTTP route from `web` to `service` for authorization and transaction boundaries, then to `repository` for the SQL. For example, a pickup status change enters `PickupController.change`, passes the participant and transition checks in `PickupService.change`, and locks and updates the row through `PickupRepository`.

## Request flow

```text
Browser -> Shelf client/server proxy -> Spring API
              |                          |
              +-> Neon Auth              +-> RequestIdFilter  (X-Request-Id)
                  session -> signed JWT      |
                                             v
                                     Spring Security
                                     | neon: verify JWT with branch JWKS
                                     | session: restore JDBC principal + check CSRF
                                             |
                                             v
                                   feature/web controller
                                             |
                                             v
                                   feature/service  ---> another feature/api
                                     |          |
                                     |          +----> feature/integration (R2, ClamAV, SMTP)
                                     v
                                   feature/repository -> PostgreSQL
                                             |
                               response DTO or ProblemDetail -> caller
```

### Neon Auth and Spring

The client server forwards the signed access token in the Authorization bearer header. Spring validates its signature, algorithm, expiration, subject, issuer and audience. The issuer and audience use the origin of the configured branch Auth URL. A valid identity resolves to a Shelf member by `neon_subject`. The token supplies the verified-email flag and Shelf supplies the member ID and role. A collision with a legacy email requires explicit account linking. Campus access also requires a verified institution match. Creating a Neon user alone does not create an authenticated Shelf request.

The session-based profile instead authenticates with a password or legacy Google OAuth, rotates the password-login session ID, and stores the security context in Spring Session JDBC. The credential version invalidates previous password sessions after a reset. The session converter recognizes both historical serialized principal names.

## Module ownership and database map

| Feature | Routes under /api | PostgreSQL tables | Responsibility |
| --- | --- | --- | --- |
| accounts | auth, oauth2, login | accounts.member, accounts.challenge, public.spring_session, public.spring_session_attributes | Member identity, profiles, email challenges and roles |
| institutions | institutions | institutions.institution, course, affiliation, request | Directory and verified campus membership |
| assets | assets | assets.file, assets.quota | Validate and scan uploads, reserve quota, store private bytes in R2 and sign authorized downloads |
| listings | explore, campus, listings, seller | listings.seller, listings.listing | Seller enrollment, drafts, publication and scoped discovery |
| moderation | moderation | moderation.decision, report, appeal | Review sellers/listings and record decisions, reports and appeals |
| pickup | pickup | pickup.request | Campus participants, reservations, meeting details and state transitions |
| commerce | commerce | None | Read-only ledger/payout overview and disabled digital transaction endpoints |
| capabilities | capabilities | None | Read availability from the assets and commerce APIs |
| common | health | None | Liveness, framework configuration, request context and HTTP errors |

Unqualified table names in a row share that row's schema. Flyway owns migration history and installs `pg_trgm` for listing search. Listings refer to accounts, verified institutions, and private asset IDs. Pickup requests retain the buyer, seller, listing, and campus IDs. The schema and constraints for each relationship are in the [migrations](../src/main/resources/db/migration). Append a new migration when the schema changes.

## Adding a feature operation

1. Put HTTP request records, validation, response mapping and the controller in the feature's `web` package. Keep private database fields out of responses by selecting response fields explicitly.
2. Put state transitions and transaction boundaries in `service`. Keep feature values in `domain`, SQL and row mapping in `repository`, and provider clients in `integration`.
3. Publish a narrow interface or DTO in `api` when another feature needs it. Spring Modulith verifies named interfaces and cycles. ArchUnit restricts JDBC and rejects cross-feature access outside `api`. [Architecture tests](../src/test/java/com/shelf/api/ArchitectureTest.java)
4. Put behavioral tests under the matching feature. Shared HTTP fixtures provide a real server and a disposable database. Preserve the current route, success payload, status and authorization tests when moving code.

## Tracing failures

Send a short `X-Request-Id` or use the ID returned by the API. Look for that value in the response's `traceId` and server logs. Logs include the authenticated Shelf user ID when available. The completion event includes the HTTP method, matched route template, status, and duration. Production emits ECS JSON.

MVC and security errors return `application/problem+json` with `status`, `title`, `detail`, `code` and `traceId`. The Google browser login flow retains its redirects. The public health endpoint reports process liveness. Use a real application operation to verify a database or provider integration.
