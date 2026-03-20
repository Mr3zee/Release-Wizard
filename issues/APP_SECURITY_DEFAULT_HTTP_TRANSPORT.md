# APP_SECURITY_DEFAULT_HTTP_TRANSPORT

## Severity: Medium
## Category: Transport Security
## Affected Screens: All (LoginScreen, ReleaseDetailScreen WebSocket)

### Description

The JVM Desktop client defaults to `http://localhost:8080` and `ws://localhost:8080`. There is no validation that the configured URL uses HTTPS/WSS. In production, credentials (username + plaintext password in JSON body) and all WebSocket traffic (release data, block execution updates, session cookies) would be sent over unencrypted HTTP.

The server sets `Strict-Transport-Security` headers which help for Web clients but do not affect Desktop JVM HTTP requests.

### Impact

Credentials and all application data transmitted in plaintext over the network if HTTPS is not explicitly configured.

### Affected Locations

- `composeApp/src/jvmMain/.../api/PlatformUrl.jvm.kt:5,9-10` — defaults to `http://` and `ws://`

### Recommendation

Add a runtime check in non-development builds that rejects or warns when `SERVER_URL` does not start with `https://`. Derive WebSocket URL automatically from HTTP URL (`https:` → `wss:`).
