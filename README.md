# Shelf API

Shelf runs a campus notes and pickup marketplace as one Java 21 Spring Boot application. Each feature owns its endpoints, business rules, and PostgreSQL queries. Start in the feature that owns the route you are debugging. [Module and schema map](docs/ARCHITECTURE.md)

## Start the API

With Maven, Node.js 22+, and an existing Neon development-branch link, run these commands from the repository root.

```sh
npm --prefix tooling/neon ci
npm --prefix tooling/neon run dev:neon
```

In a second terminal, check the running process.

```sh
curl -i http://localhost:8080/api/health
```

Expect HTTP 200, an `X-Request-Id` header, and `{"status":"up"}`. First-time setup, native PostgreSQL, email, Google OAuth, and production environment settings are covered in the [local setup guide](docs/LOCAL_SETUP.md). Safe example values are in [.env.example](.env.example).

## Test and build

```sh
mvn test
mvn package
```

HTTP tests run Flyway and exercise a real PostgreSQL database, session cookies, and authorization. Testcontainers provisions PostgreSQL by default and requires a running Docker-compatible runtime. Developers using native PostgreSQL can supply a dedicated test database. The deployed application needs Java and PostgreSQL. [Test setup](docs/LOCAL_SETUP.md#run-the-tests)

## Repository map

| Path | Contents |
| --- | --- |
| [src/main/java/com/shelf/api](src/main/java/com/shelf/api) | Application entry point, eight feature modules, shared infrastructure |
| [src/main/resources](src/main/resources) | Spring profiles and versioned Flyway migrations |
| [src/test/java/com/shelf/api](src/test/java/com/shelf/api) | Module rules, feature tests, shared PostgreSQL and HTTP fixtures |
| [tooling/neon](tooling/neon) | Neon branch configuration and development launcher |
| [.github/workflows/ci.yml](.github/workflows/ci.yml) | Maven tests on pull requests and pushes to main |
