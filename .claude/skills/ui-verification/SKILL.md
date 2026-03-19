---
name: ui-verification
description: Run visual UI verification and UX audit of the Release Wizard Compose Desktop app. Starts the full environment (PostgreSQL in Docker, Ktor server, Compose app with test server), registers a test user, and provides tools to navigate, interact, and screenshot every screen. Use this skill whenever you need to visually verify UI changes, audit UX flows, check for visual regressions, take screenshots of specific screens, or do any kind of manual UI testing. Also use when the user asks to "check how it looks", "take a screenshot", "verify the UI", "visual test", "audit the screens", or "run the app and check". This is the ONLY way to visually inspect the running Compose Desktop application.
argument-hint: "[screen or area to verify]"
---

# UI Verification

Runs the full Release Wizard stack and provides interactive UI control via HTTP for visual verification.

## Parallel Execution

Multiple agents may run UI verification simultaneously. **Always use unique ports** to avoid clashes. Pick a port set based on your worktree name or a random suffix:

| Service | Default Port | Env Var to Override |
|---------|-------------|---------------------|
| PostgreSQL (Docker) | 5432 | Docker `-p` flag |
| Ktor Server | 8080 | `PORT` |
| Compose UI Test Server | 54345 | `COMPOSE_UI_TEST_SERVER_PORT` (native lib support) |
| Compose → Server HTTP URL | http://localhost:8080 | `SERVER_URL` |
| Compose → Server WS URL | ws://localhost:8080 | `SERVER_WS_URL` |

Example port sets: `5432/8080/54345` (default), `5433/8081/54346`, `5434/8082/54347`.

**IMPORTANT:** Never use `./gradlew --stop` — it kills Gradle daemons for ALL worktrees, crashing other agents' running servers. Use `nohup` for background Gradle processes and kill by PID when done.

## Environment Setup

Follow these steps in order. Each step must succeed before proceeding. The examples below use default ports — replace with your unique ports when running in parallel.

### Step 1: Start PostgreSQL in Docker

```bash
# Use a unique container name and port when running in parallel
docker ps --filter name=rw-postgres --format '{{.Status}}' | grep -q Up && echo "Already running" || \
docker run -d \
  --name rw-postgres \
  -e POSTGRES_DB=release_wizard \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:14-alpine
```

Wait for readiness:
```bash
for i in $(seq 1 30); do
  docker exec rw-postgres pg_isready -U postgres 2>/dev/null && break
  sleep 1
done
```

If port 5432 is already in use, use a different port (`-p 5433:5432`) and update `DB_URL` accordingly.

### Step 2: Start the Ktor Server

Run in the background with `nohup` and dev-friendly env vars:

```bash
PORT=8080 \
AUTH_SESSION_SIGN_KEY="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef" \
ENCRYPTION_KEY="YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=" \
PASSWORD_MIN_LENGTH=4 \
PASSWORD_REQUIRE_UPPERCASE=false \
PASSWORD_REQUIRE_DIGIT=false \
PASSWORD_REQUIRE_SPECIAL=false \
SECURE_COOKIE=false \
DB_USER=postgres \
DB_PASSWORD=postgres \
DB_URL="jdbc:postgresql://localhost:5432/release_wizard" \
CORS_ALLOWED_ORIGIN_1="http://localhost:8080" \
nohup ./gradlew :server:run > /tmp/rw_server.log 2>&1 &
```

Wait for the server to respond:
```bash
for i in $(seq 1 60); do
  curl -s http://localhost:8080/ 2>/dev/null | grep -q "Release Wizard" && break
  sleep 2
done
```

### Step 3: Start the Compose Desktop App

```bash
COMPOSE_UI_TEST_SERVER_ENABLED=true \
COMPOSE_UI_TEST_SERVER_PORT=54345 \
SERVER_URL=http://localhost:8080 \
SERVER_WS_URL=ws://localhost:8080 \
nohup ./gradlew :composeApp:run > /tmp/rw_compose.log 2>&1 &
```

Wait for the compose-ui-test-server:
```bash
for i in $(seq 1 90); do
  curl -s http://localhost:54345/health 2>/dev/null | grep -q "OK" && break
  sleep 2
done
```

### Step 4: Register Test User and Create Team

The app starts on the login screen. Register and set up test data:

```bash
# Switch to register mode
curl -s "http://localhost:54345/onNodeWithText/Don't%20have%20an%20account%3F%20Register/performClick"
curl -s "http://localhost:54345/waitForIdle"

# Fill registration
curl -s "http://localhost:54345/onNodeWithTag/login_username/performTextInput?text=testuser"
curl -s "http://localhost:54345/waitForIdle"
curl -s "http://localhost:54345/onNodeWithTag/login_password/performTextInput?text=test"
curl -s "http://localhost:54345/waitForIdle"
curl -s "http://localhost:54345/onNodeWithTag/register_button/performClick"
sleep 3
curl -s "http://localhost:54345/waitForIdle"

# Create a team (arrives at Teams screen after registration)
# The FAB opens an inline form (not a dialog) at the top of the list
curl -s "http://localhost:54345/onNodeWithTag/create_team_fab/performClick"
sleep 1
curl -s "http://localhost:54345/waitForIdle"
curl -s "http://localhost:54345/onNodeWithTag/team_name_input/performTextInput?text=Test%20Team"
curl -s "http://localhost:54345/waitForIdle"
curl -s "http://localhost:54345/onNodeWithTag/create_team_confirm/performClick"
sleep 2
curl -s "http://localhost:54345/waitForIdle"
# Now on Project List screen for "Test Team"
```

If the user already exists (registration fails), log in instead:
```bash
curl -s "http://localhost:54345/onNodeWithTag/login_username/performTextInput?text=testuser"
curl -s "http://localhost:54345/waitForIdle"
curl -s "http://localhost:54345/onNodeWithTag/login_password/performTextInput?text=test"
curl -s "http://localhost:54345/waitForIdle"
curl -s "http://localhost:54345/onNodeWithTag/login_button/performClick"
sleep 3
curl -s "http://localhost:54345/waitForIdle"
```

## Interacting With the UI

All interaction goes through compose-ui-test-server on `http://localhost:54345`.

### Core Pattern

Always follow this sequence:
1. Perform action (click, type)
2. `waitForIdle` — let UI settle
3. Take screenshot to verify

### Available Endpoints

| Endpoint | Use |
|----------|-----|
| `GET /health` | Check server is alive |
| `GET /waitForIdle` | Wait for UI to stabilize after any action |
| `GET /onNodeWithTag/{tag}/performClick` | Click by test tag (preferred) |
| `GET /onNodeWithTag/{tag}/performTextInput?text=...` | Type text (URL-encode!) |
| `GET /onNodeWithText/{text}/performClick` | Click by visible text (fallback) |
| `GET /waitUntilExactlyOneExists/tag/{tag}?timeout=10000` | Wait for element |
| `GET /captureScreenshot?path=/tmp/screenshot.png` | Save screenshot (absolute path) |

### Important: URL-encode text parameters
- Space: `%20`
- `@`: `%40`
- `&`: `%26`
- `?`: `%3F`
- `'`: `%27`

### Taking Screenshots

```bash
curl -s "http://localhost:54345/captureScreenshot?path=/tmp/rw_SCREENNAME.png"
```

Then view with the Read tool: `Read /tmp/rw_SCREENNAME.png`

### Known Limitations

1. **DropdownMenu content is invisible in screenshots.** Screenshots succeed (no HTTP 500) but the dropdown popup layer is not captured — only the underlying window is shown. Dismiss the menu before screenshotting if you need to verify its content. Note: AlertDialogs have been fully removed from the app — all confirmations and forms are now inline components that ARE screenshotable.
2. **`performTextInput` appends, doesn't replace.** If a field already has text, you need to clear it first or click elsewhere and come back.
3. **Canvas blocks are not directly clickable by test tag.** The DAG canvas is a custom Canvas composable. You can click blocks by their visible text label using `onNodeWithText`.
4. **Inline confirmations and forms are screenshotable.** The app uses `RwInlineConfirmation` banners and `RwInlineForm` cards instead of AlertDialogs. These render in the normal composition tree and can be captured in screenshots.

## Navigation Map

The app has this screen hierarchy (use Back to go up):

```
Login/Register
  |
  v
Teams List (first visit after register)
  |
  v
Project List (home screen after team selected)
  |-- [Sidebar] Teams
  |     |-- Team Detail -> Audit Log, Manage
  |     |-- My Invites
  |-- [Sidebar] Releases
  |     |-- Release Detail
  |-- [Sidebar] Connections
  |     |-- Connection Form (create/edit)
  |-- [Project click] -> DAG Editor
  |     |-- Automation
  |-- [Sidebar Settings] -> Theme, Language, Shortcuts
  |-- [Sidebar Sign Out] -> Login
```

### Sidebar Navigation Test Tags

The app uses a persistent sidebar for navigation (no nav buttons in ProjectList TopAppBar).

| Tag | Destination |
|-----|-------------|
| `sidebar_nav_projects` | Projects List |
| `sidebar_nav_releases` | Releases List |
| `sidebar_nav_connections` | Connections List |
| `sidebar_nav_teams` | Teams List |
| `sidebar_sign_out` | Sign Out (two-click confirmation) |
| `sidebar_settings` | Settings (expandable section) |
| `sidebar_team_switcher` | Team dropdown |
| `sidebar_collapse_toggle` | Collapse/expand sidebar |

### Common Screen Entry Points

**From any top-level screen (sidebar visible):**
```bash
# Open editor for a project
curl -s "http://localhost:54345/onNodeWithText/Test%20Project/performClick"

# Navigate to Releases
curl -s "http://localhost:54345/onNodeWithTag/sidebar_nav_releases/performClick"

# Navigate to Connections
curl -s "http://localhost:54345/onNodeWithTag/sidebar_nav_connections/performClick"

# Navigate to Teams
curl -s "http://localhost:54345/onNodeWithTag/sidebar_nav_teams/performClick"
```

**Going back (from detail screens — sidebar is hidden, Back button shown):**
```bash
curl -s "http://localhost:54345/onNodeWithTag/back_button/performClick"
# or if no back_button tag:
curl -s "http://localhost:54345/onNodeWithText/Back/performClick"
```

## Cleanup

When done, stop services by PID (never use `./gradlew --stop`):

```bash
# Find and kill the server and app Gradle processes for this worktree
ps aux | grep "gradlew" | grep "<worktree-name>" | grep -v grep | awk '{print $2}' | xargs kill 2>/dev/null

# Stop and remove the PostgreSQL container
docker stop rw-postgres && docker rm rw-postgres
```

Or to keep the container for next time:
```bash
docker stop rw-postgres
# Next time: docker start rw-postgres
```
