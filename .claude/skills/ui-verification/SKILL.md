---
name: ui-verification
description: Run visual UI verification and UX audit of the Release Wizard Compose Desktop app. Starts the full environment (PostgreSQL in Docker, Ktor server, Compose app with test server), registers a test user, and provides tools to navigate, interact, and screenshot every screen. Use this skill whenever you need to visually verify UI changes, audit UX flows, check for visual regressions, take screenshots of specific screens, or do any kind of manual UI testing. Also use when the user asks to "check how it looks", "take a screenshot", "verify the UI", "visual test", "audit the screens", or "run the app and check". This is the ONLY way to visually inspect the running Compose Desktop application.
argument-hint: "[screen or area to verify]"
---

# UI Verification

Runs the full Release Wizard stack and provides interactive UI control via HTTP for visual verification.

## Environment Setup

Follow these steps in order. Each step must succeed before proceeding.

### Step 1: Start PostgreSQL in Docker

```bash
# Check if the container already exists and is running
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

If port 5432 is already in use by a host PostgreSQL, either stop it first (`brew services stop postgresql@14`) or use a different port (`-p 5433:5432`) and update `DB_URL` accordingly.

### Step 2: Start the Ktor Server

Run in the background with dev-friendly env vars:

```bash
AUTH_SESSION_SIGN_KEY="01234567890123456789012345678901" \
ENCRYPTION_KEY="01234567890123456789012345678901" \
PASSWORD_MIN_LENGTH=4 \
PASSWORD_REQUIRE_UPPERCASE=false \
PASSWORD_REQUIRE_DIGIT=false \
PASSWORD_REQUIRE_SPECIAL=false \
SECURE_COOKIE=false \
DB_USER=postgres \
DB_PASSWORD=postgres \
DB_URL="jdbc:postgresql://localhost:5432/release_wizard" \
./gradlew :server:run &
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
COMPOSE_UI_TEST_SERVER_ENABLED=true ./gradlew :composeApp:run &
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
curl -s "http://localhost:54345/onNodeWithTag/create_team_fab/performClick"
sleep 1
curl -s "http://localhost:54345/onNodeWithTag/team_name_input/performTextInput?text=Test%20Team"
curl -s "http://localhost:54345/waitForIdle"
curl -s "http://localhost:54345/onNodeWithText/Create/performClick"
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

1. **Screenshots fail (HTTP 500) when a popup/dropdown is open.** AlertDialogs, DropdownMenus, and overflow menus prevent screenshot capture. Dismiss the popup first, or just interact blind and screenshot after.
2. **`performTextInput` appends, doesn't replace.** If a field already has text, you need to clear it first or click elsewhere and come back.
3. **Canvas blocks are not directly clickable by test tag.** The DAG canvas is a custom Canvas composable. You can click blocks by their visible text label using `onNodeWithText`.

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
  |-- Teams (nav bar)
  |     |-- Team Detail -> Audit Log, Manage
  |     |-- My Invites
  |-- Releases (nav bar)
  |     |-- Release Detail
  |-- Connections (nav bar)
  |     |-- Connection Form (create/edit)
  |-- [Project click] -> DAG Editor
  |     |-- Automation
  |-- [Kebab menu] -> Theme, Language, Shortcuts
```

### Navigation Test Tags (Project List nav bar)

| Tag | Destination |
|-----|-------------|
| `teams_button` | Teams List |
| `releases_button` | Releases List |
| `connections_button` | Connections List |
| `logout_button` | Sign Out |
| `overflow_menu_button` | Settings dropdown |

### Common Screen Entry Points

**From Project List:**
```bash
# Open editor for a project
curl -s "http://localhost:54345/onNodeWithText/Test%20Project/performClick"

# Navigate to Releases
curl -s "http://localhost:54345/onNodeWithTag/releases_button/performClick"

# Navigate to Connections
curl -s "http://localhost:54345/onNodeWithTag/connections_button/performClick"

# Navigate to Teams
curl -s "http://localhost:54345/onNodeWithTag/teams_button/performClick"
```

**Going back (from any sub-screen):**
```bash
curl -s "http://localhost:54345/onNodeWithTag/back_button/performClick"
# or if no back_button tag:
curl -s "http://localhost:54345/onNodeWithText/Back/performClick"
```

## Cleanup

When done, stop all services:

```bash
# Stop Gradle daemons (server + app)
./gradlew --stop

# Stop and remove the PostgreSQL container
docker stop rw-postgres && docker rm rw-postgres
```

Or to keep the container for next time:
```bash
docker stop rw-postgres
# Next time: docker start rw-postgres
```
