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

**Always use local split mode** — start the server with `./gradlew :server:run` (API only, no frontend bundling). Never use `:server:runApp` for UI verification — it would bundle the WasmJS frontend and serve it from the same origin, but we need the Desktop app instead.

Follow these steps in order. Each step must succeed before proceeding. **Each agent must pick a unique port set** to allow fully independent parallel execution:

### Step 0: Pick unique ports

Choose a port set that no other agent is using. Derive from worktree name hash or pick from the table:

| Agent | DB_PORT | SERVER_PORT | UI_TEST_PORT |
|-------|---------|-------------|--------------|
| 1st   | 5432    | 8080        | 54345        |
| 2nd   | 5433    | 8081        | 54346        |
| 3rd   | 5434    | 8082        | 54347        |

Set these as shell variables for all subsequent steps:
```bash
DB_PORT=5432
SERVER_PORT=8080
UI_TEST_PORT=54345
CONTAINER_NAME="rw-postgres-${SERVER_PORT}"
```

### Step 1: Start PostgreSQL and generate env

```bash
# Start a dedicated PostgreSQL container for this agent
docker ps --filter name=$CONTAINER_NAME --format '{{.Status}}' | grep -q Up && echo "Already running" || \
docker run -d \
  --name $CONTAINER_NAME \
  -e POSTGRES_DB=release_wizard \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p $DB_PORT:5432 \
  postgres:18-alpine

# Generate .env if not present (provides auth secrets)
./scripts/setup-local-env.sh
```

Wait for DB readiness:
```bash
for i in $(seq 1 30); do
  docker exec $CONTAINER_NAME pg_isready -U postgres -d release_wizard 2>/dev/null && break
  sleep 1
done
```

### Step 2: Start the Ktor Server

Source `.env` for secrets, override ports and password policy for testing:

```bash
source .env
PORT=$SERVER_PORT \
DB_URL="jdbc:postgresql://localhost:$DB_PORT/release_wizard" \
PASSWORD_MIN_LENGTH=4 \
PASSWORD_REQUIRE_UPPERCASE=false \
PASSWORD_REQUIRE_DIGIT=false \
PASSWORD_REQUIRE_SPECIAL=false \
nohup ./gradlew :server:run > /tmp/rw_server_${SERVER_PORT}.log 2>&1 &
```

Wait for the server to respond:
```bash
for i in $(seq 1 60); do
  curl -s http://localhost:$SERVER_PORT/health 2>/dev/null | grep -q "UP" && break
  sleep 2
done
```

### Step 3: Start the Compose Desktop App

```bash
COMPOSE_UI_TEST_SERVER_ENABLED=true \
COMPOSE_UI_TEST_SERVER_PORT=$UI_TEST_PORT \
SERVER_URL=http://localhost:$SERVER_PORT \
SERVER_WS_URL=ws://localhost:$SERVER_PORT \
nohup ./gradlew :composeApp:run > /tmp/rw_compose_${SERVER_PORT}.log 2>&1 &
```

Wait for the compose-ui-test-server:
```bash
for i in $(seq 1 90); do
  curl -s http://localhost:$UI_TEST_PORT/health 2>/dev/null | grep -q "OK" && break
  sleep 2
done
```

### Step 4: Register Test User and Create Team

The app starts on the login screen. Register and set up test data:

```bash
# Switch to register mode
curl -s "http://localhost:$UI_TEST_PORT/onNodeWithText/Don't%20have%20an%20account%3F%20Register/performClick"
curl -s "http://localhost:$UI_TEST_PORT/waitForIdle"

# Fill registration
curl -s "http://localhost:$UI_TEST_PORT/onNodeWithTag/login_username/performTextInput?text=testuser"
curl -s "http://localhost:$UI_TEST_PORT/waitForIdle"
curl -s "http://localhost:$UI_TEST_PORT/onNodeWithTag/login_password/performTextInput?text=test"
curl -s "http://localhost:$UI_TEST_PORT/waitForIdle"
curl -s "http://localhost:$UI_TEST_PORT/onNodeWithTag/register_button/performClick"
sleep 3
curl -s "http://localhost:$UI_TEST_PORT/waitForIdle"

# Create a team (arrives at Teams screen after registration)
# The FAB opens an inline form (not a dialog) at the top of the list
curl -s "http://localhost:$UI_TEST_PORT/onNodeWithTag/create_team_fab/performClick"
sleep 1
curl -s "http://localhost:$UI_TEST_PORT/waitForIdle"
curl -s "http://localhost:$UI_TEST_PORT/onNodeWithTag/team_name_input/performTextInput?text=Test%20Team"
curl -s "http://localhost:$UI_TEST_PORT/waitForIdle"
curl -s "http://localhost:$UI_TEST_PORT/onNodeWithTag/create_team_confirm/performClick"
sleep 2
curl -s "http://localhost:$UI_TEST_PORT/waitForIdle"
# Now on Project List screen for "Test Team"
```

If the user already exists (registration fails), log in instead:
```bash
curl -s "http://localhost:$UI_TEST_PORT/onNodeWithTag/login_username/performTextInput?text=testuser"
curl -s "http://localhost:$UI_TEST_PORT/waitForIdle"
curl -s "http://localhost:$UI_TEST_PORT/onNodeWithTag/login_password/performTextInput?text=test"
curl -s "http://localhost:$UI_TEST_PORT/waitForIdle"
curl -s "http://localhost:$UI_TEST_PORT/onNodeWithTag/login_button/performClick"
sleep 3
curl -s "http://localhost:$UI_TEST_PORT/waitForIdle"
```

## Interacting With the UI

All interaction goes through compose-ui-test-server on `http://localhost:$UI_TEST_PORT`.

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
curl -s "http://localhost:$UI_TEST_PORT/captureScreenshot?path=/tmp/rw_SCREENNAME.png"
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
curl -s "http://localhost:$UI_TEST_PORT/onNodeWithText/Test%20Project/performClick"

# Navigate to Releases
curl -s "http://localhost:$UI_TEST_PORT/onNodeWithTag/sidebar_nav_releases/performClick"

# Navigate to Connections
curl -s "http://localhost:$UI_TEST_PORT/onNodeWithTag/sidebar_nav_connections/performClick"

# Navigate to Teams
curl -s "http://localhost:$UI_TEST_PORT/onNodeWithTag/sidebar_nav_teams/performClick"
```

**Going back (from detail screens — sidebar is hidden, Back button shown):**
```bash
curl -s "http://localhost:$UI_TEST_PORT/onNodeWithTag/back_button/performClick"
# or if no back_button tag:
curl -s "http://localhost:$UI_TEST_PORT/onNodeWithText/Back/performClick"
```

## Cleanup

When done, stop services by PID (never use `./gradlew --stop`):

```bash
# Find and kill the server and app Gradle processes for this worktree
ps aux | grep "gradlew" | grep "<worktree-name>" | grep -v grep | awk '{print $2}' | xargs kill 2>/dev/null

# Stop and remove the PostgreSQL container
docker stop $CONTAINER_NAME && docker rm $CONTAINER_NAME
```
