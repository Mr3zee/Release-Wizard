#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$PROJECT_ROOT/.env"
EXAMPLE_FILE="$PROJECT_ROOT/.env.example"

if [ -f "$ENV_FILE" ]; then
    echo ".env already exists — skipping. Delete it and re-run to regenerate."
    exit 0
fi

if [ ! -f "$EXAMPLE_FILE" ]; then
    echo "ERROR: .env.example not found at $EXAMPLE_FILE"
    exit 1
fi

command -v openssl >/dev/null 2>&1 || { echo "ERROR: openssl is required but not found"; exit 1; }

echo "Generating .env from .env.example..."

# Generate secrets (tr -d '\n' strips trailing newline from base64 output)
SIGN_KEY=$(openssl rand -hex 32)
ENCRYPT_KEY=$(openssl rand -base64 32 | tr -d '\n')

# Copy template and replace placeholders
cp "$EXAMPLE_FILE" "$ENV_FILE"

# Replace CHANGE_ME placeholders with generated values
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS sed requires '' after -i
    sed -i '' "s|^AUTH_SESSION_SIGN_KEY=CHANGE_ME|AUTH_SESSION_SIGN_KEY=$SIGN_KEY|" "$ENV_FILE"
    sed -i '' "s|^ENCRYPTION_KEY=CHANGE_ME|ENCRYPTION_KEY=$ENCRYPT_KEY|" "$ENV_FILE"
else
    sed -i "s|^AUTH_SESSION_SIGN_KEY=CHANGE_ME|AUTH_SESSION_SIGN_KEY=$SIGN_KEY|" "$ENV_FILE"
    sed -i "s|^ENCRYPTION_KEY=CHANGE_ME|ENCRYPTION_KEY=$ENCRYPT_KEY|" "$ENV_FILE"
fi

echo "Done. Generated .env with fresh secrets."
echo "Run: source .env && ./gradlew :server:run"
