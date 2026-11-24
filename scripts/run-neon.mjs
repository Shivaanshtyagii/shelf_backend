import { spawn } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { parseEnv } from "node:util";

function jdbcConnection(value) {
  const url = new URL(value ?? "");
  if (!["postgres:", "postgresql:"].includes(url.protocol) || !url.hostname || !url.username || !url.password) {
    throw new Error();
  }
  return {
    url: `jdbc:postgresql://${url.host}${url.pathname}?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory`,
    user: decodeURIComponent(url.username),
    password: decodeURIComponent(url.password),
  };
}

function start() {
  const file = existsSync(".env") ? ".env" : ".env.local";
  const config = { ...parseEnv(readFileSync(file, "utf8")), ...process.env };
  if (!config.NEON_BRANCH || config.NEON_BRANCH === "production") {
    console.error("Run this local launcher with Neon variables pulled from a development branch.");
    process.exitCode = 1;
    return;
  }
  if (!config.NEON_AUTH_BASE_URL || !config.NEON_AUTH_JWKS_URL) throw new Error();
  const runtime = jdbcConnection(config.DATABASE_URL);
  const migration = jdbcConnection(config.DATABASE_URL_UNPOOLED);
  const backend = spawn("mvn", ["spring-boot:run"], {
    stdio: "inherit",
    env: {
      ...process.env,
      SPRING_PROFILES_ACTIVE: "neon",
      NEON_AUTH_BASE_URL: config.NEON_AUTH_BASE_URL,
      NEON_AUTH_JWKS_URL: config.NEON_AUTH_JWKS_URL,
      SHELF_DB_URL: runtime.url,
      SHELF_DB_USER: runtime.user,
      SHELF_DB_PASSWORD: runtime.password,
      SPRING_FLYWAY_URL: migration.url,
      SPRING_FLYWAY_USER: migration.user,
      SPRING_FLYWAY_PASSWORD: migration.password,
    },
  });
  backend.on("error", () => {
    console.error("Could not start Maven. Verify mvn is on PATH.");
    process.exitCode = 1;
  });
  backend.on("exit", (code, signal) => {
    process.exitCode = code ?? (signal === "SIGINT" ? 130 : 1);
  });
}

try { start(); }
catch {
  console.error("Check the DATABASE_URL, DATABASE_URL_UNPOOLED, and Neon Auth settings in the backend environment file.");
  process.exitCode = 1;
}
