# Deployment Guide

## Prerequisites

- Docker & Docker Compose
- JDK 21+
- Gradle (via wrapper)

## Quick Start (Bundled Mode)

```bash
docker compose up -d                    # Start PostgreSQL
./scripts/setup-local-env.sh            # Generate .env with secrets (one-time)
source .env && ./gradlew :server:runApp # Build frontend + start server
```

Open http://localhost:8080

## Local Development

### Split Mode (Frontend Hot-Reload)

Best for frontend development — separate processes with hot-reload:

```bash
docker compose up -d
./scripts/setup-local-env.sh    # one-time
source .env

# Terminal 1: backend
./gradlew :server:run

# Terminal 2: frontend (hot-reload on port 8081)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

The setup script pre-configures CORS for `http://localhost:8081`.

### Bundled Mode (Production-Like)

Server builds and serves the frontend from the same origin:

```bash
docker compose up -d
source .env && ./gradlew :server:runApp
```

No CORS needed — everything on `http://localhost:8080`.

### Key Difference

- `./gradlew :server:run` — starts API only, never builds frontend (fast)
- `./gradlew :server:runApp` — builds WasmJS frontend first, then serves everything

## Production (GKE)

Production runs on JetBrains Cloud Console (GKE). Infrastructure details (URLs, secret names, namespace config, OAuth2 setup) are maintained separately — not checked into source control.

### High-Level Steps

1. **Create Database** — provision PostgreSQL via Cloud Console
2. **Create Application Secrets** — generate session signing/encryption keys via `kubectl create secret`
3. **Create Deployment** — configure via Cloud Console (port 8080, main branch)
4. **Configure Additional Parameters** — ingress class, health path, secrets, DB env var mapping, webhook URL
5. **Build & Deploy** — `./gradlew :server:jib` compiles WasmJS frontend, bundles into server, builds container image via Jib

### Key Design Decisions

- **`customEnv` for DB**: Cloud Console injects DB vars as `DB0_URL_JDBC`/`DB0_USERNAME`/`DB0_PASSWORD`, but the app expects `DB_URL`/`DB_USER`/`DB_PASSWORD`. Use `customEnv` with `secretKeyRef` to map them.
- **Public ingress**: Default ingress class is VPN-only. The app needs public access for webhook callbacks from GitHub, TeamCity, and Slack.
- **Base image must be full glibc (not Alpine)**: `argon2-jvm` uses JNI with a native library compiled against glibc. Alpine uses musl libc, causing SIGSEGV on startup. Jib config uses `amazoncorretto:21` (full).

### Known Issues

**Console `customEnv` can be silently dropped during redeployment.** If Console re-applies the Helm chart without the additional parameters, the deployment loses its `env` block (DB creds, webhook URL). The new pods crash-loop with `Connection to localhost:5432 refused` because they fall back to defaults. The old ReplicaSet keeps running (rolling update), masking the issue. Fix: `kubectl patch` the deployment to restore the `env` entries, then verify Console additional parameters are saved.

**Ktor 3.x `SessionTransportTransformerEncrypt` AES-256 IV bug.** The init block calls `ivGenerator(encryptionKeySize)` instead of `ivGenerator(blockSize)`. With AES-256 (32-byte key), this generates a 32-byte IV instead of 16-byte, crashing with "Wrong IV length". Workaround: provide a custom `ivGenerator` that always produces 16 bytes. See `Application.kt` session setup. Tracked as [KTOR-661](https://youtrack.jetbrains.com/issue/KTOR-661), still present in Ktor 3.4.1.

## Google OAuth Setup

Google OAuth provides "Sign in with Google" for browser (WasmJS) clients. Optional — without credentials, the Google button is hidden.

### Google Cloud Console

1. Go to [Google Cloud Console](https://console.cloud.google.com/) → APIs & Services → Credentials
2. Create OAuth 2.0 Client ID (type: Web application)
3. Add **Authorized redirect URIs**:
   - Local: `http://localhost:8080/api/v1/auth/oauth/google/callback`
   - Prod: `https://release-wizard-prod.labs.jb.gg/api/v1/auth/oauth/google/callback`

### Local

Add to `.env` (already gitignored):
```bash
GOOGLE_OAUTH_CLIENT_ID=your-client-id.apps.googleusercontent.com
GOOGLE_OAUTH_CLIENT_SECRET=your-client-secret
```

The callback URL is auto-constructed from `WEBHOOK_BASE_URL` (defaults to `http://localhost:8080`).

### Production (GKE)

Add to the `rw-secrets` Kubernetes secret:
```bash
kubectl -n release-wizard patch secret rw-secrets -p '{"stringData":{
  "GOOGLE_OAUTH_CLIENT_ID":"your-client-id.apps.googleusercontent.com",
  "GOOGLE_OAUTH_CLIENT_SECRET":"your-client-secret"
}}'
```

Callback URL is constructed from `WEBHOOK_BASE_URL` (`https://release-wizard-prod.labs.jb.gg`).

If the oauth2-proxy ingress is deployed later, add the OAuth callback to the webhook bypass ingress:
```yaml
- path: /api/v1/auth/oauth
  pathType: Prefix
  backend:
    service:
      name: release-wizard-prod
      port:
        number: 80
```

### OAuth-Only Users

OAuth users created via Google sign-in have no password. They can:
- Set a password later (profile → "Set Password")
- Be given a password via admin "Generate Set Password Link"
- Change username / delete account without password verification

## Environment Variables

| Variable | Default | Required | Description |
|----------|---------|----------|-------------|
| `PORT` | `8080` | No | Server port |
| `HOST` | `0.0.0.0` | No | Bind address |
| `DB_URL` | `jdbc:postgresql://localhost:5432/release_wizard` | No | JDBC URL |
| `DB_USER` | `postgres` | No | DB username |
| `DB_PASSWORD` | `postgres` | No | DB password |
| `DB_MAX_POOL_SIZE` | `10` | No | HikariCP pool size |
| `AUTH_SESSION_SIGN_KEY` | — | **Yes** | Session signing key (64+ hex chars). Generate: `openssl rand -hex 32` |
| `AUTH_SESSION_ENCRYPT_KEY` | — | Recommended | Session encryption key (32 or 64 hex chars). Generate: `openssl rand -hex 32` |
| `ENCRYPTION_KEY` | — | **Yes** | AES-256 key for stored credentials (Base64, 32 bytes). Generate: `openssl rand -base64 32` |
| `WEBHOOK_BASE_URL` | `http://localhost:8080` | No | Base URL for webhook callbacks. Set to public URL in production |
| `CORS_ALLOWED_ORIGIN_1` | — | No | Allowed CORS origin (for split-mode dev) |
| `PASSWORD_PEPPER` | — | Recommended | HMAC-SHA256 password pepper (Base64, 32 bytes). Generate: `openssl rand -base64 32`. When set, passwords are HMAC'd before Argon2 hashing |
| `PASSWORD_PEPPER_OLD` | — | No | Previous pepper for rotation. Set when rotating to a new `PASSWORD_PEPPER` value |
| `SECURE_COOKIE` | `false` | No | Must be `true` in production (TLS terminated at ingress). Defaults to `false` for local dev convenience |
| `APP_VERSION` | `dev` | No | Version string |
| `GOOGLE_OAUTH_CLIENT_ID` | — | No | Google OAuth client ID. Leave blank to disable Google sign-in |
| `GOOGLE_OAUTH_CLIENT_SECRET` | — | No | Google OAuth client secret |

## Container Image

Built with [Google Jib](https://github.com/GoogleContainerTools/jib) (Gradle plugin):
- **Base**: `amazoncorretto:21` (full glibc — required for argon2-jvm native library)
- **User**: `65534` (nobody, non-root)
- **Port**: 8080
- **JVM**: `-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0`

### Local Image Build

```bash
./gradlew :server:jibDockerBuild    # Build to local Docker daemon
docker run --env-file .env -p 8080:8080 release-wizard:1.0.0
```
