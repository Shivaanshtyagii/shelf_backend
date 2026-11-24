# Local setup

Run commands from the root of this repository unless a command changes directories. Install JDK 21 and Maven, then confirm that Maven is using the intended JDK.

```sh
java -version
mvn -version
```

## Start with Neon Auth

1. Install Node.js 22+ and the Neon CLI. Check `node --version` and `neon --version`. Run `neon login` if the CLI needs authentication.
2. Install the helper dependencies. A fresh clone needs a link to the team's existing development branch. Replace the project ID below with the team's project ID. For an existing checkout with a linked branch, proceed to the environment pull.

   ```sh
   cd tooling/neon
   npm ci
   neon link --project-id YOUR_PROJECT_ID --branch dev
   neon env pull --services postgres,auth
   npm run dev:neon
   ```

3. In a separate terminal, run `curl -i http://localhost:8080/api/health`. Flyway must finish before the API serves requests. In the Shelf client, sign in and open a protected page. A direct unauthenticated request to `/api/auth/me` returns 401.

The CLI stores its ignored branch link in `tooling/neon/.neon`. The launcher reads `.env` or `.env.local` there, with a fallback to the existing root env file. Shell variables take precedence. It starts Maven at the repository root, converts the pooled and direct Neon URLs to TLS-verified JDBC connections, and passes the Auth settings to Spring. To start again from the root, use `npm --prefix tooling/neon run dev:neon`.

Managed email, verification and Google login are configured in Neon Auth. The client uses the same branch's Auth URL and forwards its access token to Spring. A 401 suggests checking the token, issuer and audience. A 403 after login can indicate an email, moderator, or campus verification requirement. The response detail and request ID identify the failing step.

## Start with native PostgreSQL

1. Provision a development database and user, for example with PostgreSQL's `createuser --pwprompt shelf` and `createdb --owner=shelf shelf` as a local administrator. The migration user needs permission to create schemas and the `pg_trgm` extension.
2. Export settings in your shell. Replace the example password with the one you chose.

   ```sh
   export SPRING_PROFILES_ACTIVE=local
   export SHELF_DB_URL='jdbc:postgresql://localhost:5432/shelf'
   export SHELF_DB_USER=shelf
   export SHELF_DB_PASSWORD='your-local-password'
   mvn spring-boot:run
   ```

3. Check `/api/health` and `/api/explore`. The local profile enables SQL statement logging and credentialed CORS for the configured `SHELF_WEB_ORIGIN`. It permits session cookies on local HTTP.

For Spring-managed email, configure the `SHELF_SMTP_*` variables, sender and public web origin before requesting verification or reset links. For legacy Google OAuth, set `SHELF_AUTH_GOOGLE_CLIENT_ID`, `SHELF_AUTH_GOOGLE_CLIENT_SECRET` and `SPRING_PROFILES_ACTIVE=local,google`. Register the exact web origin followed by `/api/login/oauth2/code/google` as the OAuth redirect URI. A moderator may approve sellers, listings, directory entries, and affiliation requests. To grant that role, an authorized operator can update `accounts.member.role` for the verified member's unique `neon_subject`.

## Run the tests

```sh
mvn test
```

With a running Docker-compatible runtime, Testcontainers starts PostgreSQL 17 for the suite and cleans it up afterward. This is the CI path. To use native PostgreSQL, create a separate disposable database with the same extension privileges and export its test credentials. The HTTP tests insert fixtures and run all migrations.

```sh
export SHELF_TEST_DB_URL='jdbc:postgresql://localhost:5432/shelf_test'
export SHELF_TEST_DB_USER=shelf_test
export SHELF_TEST_DB_PASSWORD='your-test-password'
mvn test
```

## Deploy the API

Build with `mvn package`. For managed auth, run the jar with `SPRING_PROFILES_ACTIVE=neon,prod`, the public HTTPS `SHELF_WEB_ORIGIN`, pooled `SHELF_DB_*` credentials, direct `SHELF_DB_DIRECT_*` migration credentials, and the branch's HTTPS `SHELF_AUTH_BASE_URL` and `SHELF_AUTH_JWKS_URL`. Budget the per-instance `SHELF_DB_POOL_MAX` across all replicas. The production profile enables secure cookies and structured logs. The Node helper is for development.

Enable cloud uploads only after configuring a private R2 bucket, scoped credentials, quotas, and a reachable ClamAV scanner that accepts the full 25 MB PDF limit. Accepted photos are re-encoded and authorized downloads receive short-lived links. Digital checkout, refunds, and payouts stay disabled. See the [environment reference](../.env.example) for the complete list of settings.
