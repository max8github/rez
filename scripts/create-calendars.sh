#!/usr/bin/env bash
# create-calendars.sh — Legacy helper for creating Google Calendars and printing
# the resulting "name:calendarId" pairs.
#
# Usage:
#   ./scripts/create-calendars.sh \
#     --credentials path/to/credentials.json \
#     --courts      "Court 1,Court 2,Court 3"
#
# Output (copy-pasteable):
#   Court 1:abc123@group.calendar.google.com,Court 2:def456@group.calendar.google.com,...
#
# Current Rez provisioning does not require Google Calendar creation. Use this
# only if you explicitly want to store calendarId metadata on resources.
#
# --credentials  Path to service account credentials.json.
#                Defaults to $GOOGLE_CREDENTIALS_FILE env var, then ./credentials.json.
set -euo pipefail

CREDENTIALS_FILE="${GOOGLE_CREDENTIALS_FILE:-credentials.json}"
COURTS=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --credentials) CREDENTIALS_FILE="$2"; shift 2 ;;
    --courts)      COURTS="$2";           shift 2 ;;
    *) echo "Unknown argument: $1"; exit 1 ;;
  esac
done

if [[ -z "$COURTS" ]]; then
  echo "ERROR: --courts is required"
  exit 1
fi
if [[ ! -f "$CREDENTIALS_FILE" ]]; then
  echo "ERROR: credentials file not found: $CREDENTIALS_FILE"
  echo "Pass --credentials <path> or set GOOGLE_CREDENTIALS_FILE."
  exit 1
fi

# ---- fetch access token -----------------------------------------------------
client_email=$(python3 -c "import json; print(json.load(open('$CREDENTIALS_FILE'))['client_email'])")

tmpkey=$(mktemp)
python3 -c "import json; print(json.load(open('$CREDENTIALS_FILE'))['private_key'])" > "$tmpkey"

now=$(date +%s)
exp=$((now + 3600))

header=$(printf '{"alg":"RS256","typ":"JWT"}' \
  | base64 | tr -d '=' | tr '/+' '_-' | tr -d '\n')
payload=$(printf '{"iss":"%s","scope":"https://www.googleapis.com/auth/calendar","aud":"https://oauth2.googleapis.com/token","exp":%d,"iat":%d}' \
  "$client_email" "$exp" "$now" \
  | base64 | tr -d '=' | tr '/+' '_-' | tr -d '\n')
signing_input="$header.$payload"

sig=$(printf '%s' "$signing_input" \
  | openssl dgst -sha256 -sign "$tmpkey" -binary \
  | base64 | tr -d '=' | tr '/+' '_-' | tr -d '\n')
rm -f "$tmpkey"

jwt="$signing_input.$sig"

ACCESS_TOKEN=$(curl -sf -X POST "https://oauth2.googleapis.com/token" \
  --data-urlencode "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer" \
  --data-urlencode "assertion=$jwt" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['access_token'])")

# ---- create calendars -------------------------------------------------------
RESULT=""
IFS=',' read -ra COURT_LIST <<< "$COURTS"
for NAME in "${COURT_LIST[@]}"; do
  CAL_ID=$(curl -sf -X POST "https://www.googleapis.com/calendar/v3/calendars" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"summary\": $(printf '%s' "$NAME" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read().strip()))')}" \
    | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])")
  echo "  $NAME → $CAL_ID" >&2
  RESULT="${RESULT:+$RESULT,}$NAME:$CAL_ID"
done

echo ""                            >&2
echo "Paste into --courts:"        >&2
echo "$RESULT"
