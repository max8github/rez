#!/usr/bin/env bash
# provision-facility.sh — Create a new Rez facility with its courts and register the Telegram webhook.
#
# Usage:
#   ./scripts/provision-facility.sh \
#     --host          https://rez.rezbotapp.com \
#     --webhook-host  https://rez.rezbotapp.com \
#     --name          "Erster Tennisclub Edingen-Neckarhausen" \
#     --street        "Mannheimer Str. 50" \
#     --city          "68535 Edingen-Neckarhausen" \
#     --timezone      "Europe/Berlin" \
#     --token         "123456789:ABCdef..." \
#     --admins        "987654321,111222333" \
#     --courts        "Court 1,Court 2,Court 3"
#
# --host         Base URL for the Rez API (entity creation, verification).
# --webhook-host Base URL Telegram should call for webhooks. Defaults to --host.
#                Set this separately when the API is on an internal host but webhooks
#                must go through a public tunnel (e.g. https://rez.rezbotapp.com).
#                Can also be set via REZ_BASE_URL / REZ_WEBHOOK_BASE_URL env vars.
# --courts       Comma-separated court names. The script creates a Google Calendar for
#                each court automatically via the Calendar API.
#                You can also provide existing calendar IDs as "name:calendarId" pairs
#                (or mix both forms) if calendars were already created manually.
# --credentials  Path to the Google service account credentials.json. Defaults to
#                $GOOGLE_CREDENTIALS_FILE env var, then ./credentials.json.
#                Only required when creating new calendars (i.e. --courts without IDs).
# --admins       Comma-separated list of Telegram user IDs.
set -euo pipefail

# ---- parse arguments --------------------------------------------------------
HOST="${REZ_BASE_URL:-https://rez.rezbotapp.com}"
WEBHOOK_HOST="${REZ_WEBHOOK_BASE_URL:-${REZ_BASE_URL:-https://rez.rezbotapp.com}}"
NAME=""
STREET=""
CITY=""
TIMEZONE="Europe/Berlin"
BOT_TOKEN=""
ADMINS=""
COURTS=""
CREDENTIALS_FILE="${GOOGLE_CREDENTIALS_FILE:-credentials.json}"

slugify() {
  python3 -c 'import re,sys; s=sys.argv[1].strip().lower(); s=re.sub(r"[^a-z0-9]+","-",s).strip("-"); print(s or "resource")' "$1"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host)          HOST="$2";              shift 2 ;;
    --webhook-host)  WEBHOOK_HOST="$2";      shift 2 ;;
    --name)          NAME="$2";              shift 2 ;;
    --street)        STREET="$2";            shift 2 ;;
    --city)          CITY="$2";              shift 2 ;;
    --timezone)      TIMEZONE="$2";          shift 2 ;;
    --token)         BOT_TOKEN="$2";         shift 2 ;;
    --admins)        ADMINS="$2";            shift 2 ;;
    --courts)        COURTS="$2";            shift 2 ;;
    --credentials)   CREDENTIALS_FILE="$2";  shift 2 ;;
    *) echo "Unknown argument: $1"; exit 1 ;;
  esac
done

# ---- validate ---------------------------------------------------------------
[[ -z "$WEBHOOK_HOST" ]] && WEBHOOK_HOST="$HOST"

missing=()
[[ -z "$NAME" ]]      && missing+=("--name")
[[ -z "$STREET" ]]    && missing+=("--street")
[[ -z "$CITY" ]]      && missing+=("--city")
[[ -z "$BOT_TOKEN" ]] && missing+=("--token")
[[ -z "$COURTS" ]]    && missing+=("--courts")

if [[ ${#missing[@]} -gt 0 ]]; then
  echo "ERROR: missing required arguments: ${missing[*]}"
  echo "Run with --help or read the script header for usage."
  exit 1
fi

# ---- Google Calendar helpers ------------------------------------------------

# Fetch a short-lived OAuth access token from a service account credentials.json.
# Uses openssl to sign the JWT (RS256) — no Python libraries required beyond stdlib.
get_access_token() {
  local creds_file="$1"
  local client_email tmpkey now exp header payload signing_input sig jwt

  client_email=$(python3 -c "import json; print(json.load(open('$creds_file'))['client_email'])")

  tmpkey=$(mktemp)
  # python3 decodes the JSON-escaped \n sequences into real newlines
  python3 -c "import json; print(json.load(open('$creds_file'))['private_key'])" > "$tmpkey"

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

  curl -sf -X POST "https://oauth2.googleapis.com/token" \
    --data-urlencode "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer" \
    --data-urlencode "assertion=$jwt" \
    | python3 -c "import json,sys; print(json.load(sys.stdin)['access_token'])"
}

# Create a Google Calendar with the given name and return its calendar ID.
create_calendar() {
  local name="$1" token="$2"
  curl -sf -X POST "https://www.googleapis.com/calendar/v3/calendars" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "{\"summary\": $(printf '%s' "$name" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read().strip()))')}" \
    | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])"
}

# ---- build adminUserIds JSON array ------------------------------------------
ADMIN_JSON="[]"
if [[ -n "$ADMINS" ]]; then
  ADMIN_JSON=$(echo "$ADMINS" | tr ',' '\n' | awk '{print "\"" $0 "\""}' | paste -sd ',' - | sed 's/^/[/;s/$/]/')
fi

# ---- fetch Google access token if any court needs calendar creation ---------
IFS=',' read -ra COURT_LIST <<< "$COURTS"
NEEDS_CALENDAR_CREATE=false
for ENTRY in "${COURT_LIST[@]}"; do
  [[ "$ENTRY" != *:* ]] && NEEDS_CALENDAR_CREATE=true && break
done

ACCESS_TOKEN=""
if [[ "$NEEDS_CALENDAR_CREATE" == true ]]; then
  if [[ ! -f "$CREDENTIALS_FILE" ]]; then
    echo "ERROR: credentials file not found: $CREDENTIALS_FILE"
    echo "Pass --credentials <path> or set GOOGLE_CREDENTIALS_FILE."
    exit 1
  fi
  echo ""
  echo "==> Fetching Google access token (service account: $CREDENTIALS_FILE)"
  ACCESS_TOKEN=$(get_access_token "$CREDENTIALS_FILE")
  echo "    OK"
fi

# ---- 1. create facility ------------------------------------------------------
echo ""
echo "==> Creating facility: $NAME"
FACILITY_ID=$(curl -sf -X POST "$HOST/facility/" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": $(echo "$NAME" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read().strip()))'),
    \"address\": {
      \"street\": $(echo "$STREET" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read().strip()))'),
      \"city\":   $(echo "$CITY"   | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read().strip()))')
    },
    \"timezone\": \"$TIMEZONE\",
    \"botToken\": \"$BOT_TOKEN\",
    \"adminUserIds\": $ADMIN_JSON
  }")

echo "    Facility ID: $FACILITY_ID"

# ---- 2. create courts --------------------------------------------------------
echo ""
echo "==> Creating courts"

declare -a COURT_IDS=()
COURT_INDEX=0
for ENTRY in "${COURT_LIST[@]}"; do
  COURT_INDEX=$((COURT_INDEX + 1))
  if [[ "$ENTRY" == *:* ]]; then
    COURT_NAME="${ENTRY%%:*}"
    CALENDAR_ID="${ENTRY#*:}"
  else
    COURT_NAME="$ENTRY"
    echo "    Creating Google Calendar: $COURT_NAME"
    CALENDAR_ID=$(create_calendar "$COURT_NAME" "$ACCESS_TOKEN")
    echo "    Calendar ID: $CALENDAR_ID"
  fi

  COURT_SLUG=$(slugify "$COURT_NAME")
  # Prefix with first 8 chars of facility ID so IDs are unique across facilities.
  COURT_ID="${FACILITY_ID:0:8}-${COURT_SLUG}-${COURT_INDEX}"

  curl -sf -X POST "$HOST/resource/$COURT_ID" \
    -H "Content-Type: application/json" \
    -d "{
      \"resourceId\": \"$COURT_ID\",
      \"resourceName\": $(echo "$COURT_NAME" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read().strip()))'),
      \"calendarId\": \"$CALENDAR_ID\"
    }" >/dev/null

  curl -sf -X PUT "$HOST/resource/$COURT_ID/external-ref" \
    -H "Content-Type: application/json" \
    -d "{
      \"externalRef\": \"$COURT_ID\",
      \"externalGroupRef\": \"$FACILITY_ID\"
    }" >/dev/null

  echo "    $COURT_NAME → $COURT_ID  (calendar: $CALENDAR_ID)"
  COURT_IDS+=("$COURT_NAME=$COURT_ID=$CALENDAR_ID")
done

# ---- 3. register Telegram webhook -------------------------------------------
echo ""
echo "==> Registering Telegram webhook"
WEBHOOK_URL="${WEBHOOK_HOST}/telegram/$BOT_TOKEN/webhook"
WEBHOOK_RESULT=$(curl -sf "https://api.telegram.org/bot$BOT_TOKEN/setWebhook?url=$WEBHOOK_URL")
echo "    $WEBHOOK_RESULT"

# ---- 4. verify ---------------------------------------------------------------
echo ""
echo "==> Verifying facility"
FACILITY_STATE=$(curl -sf "$HOST/facility/$FACILITY_ID")
echo "    $FACILITY_STATE"

# ---- 5. summary --------------------------------------------------------------
echo ""
echo "================================================================"
echo " PROVISIONING COMPLETE"
echo "================================================================"
echo " Facility:    $NAME"
echo " Facility ID: $FACILITY_ID"
echo " Host:        $HOST"
echo " Bot token:   ${BOT_TOKEN:0:8}..."
echo ""
echo " Courts:"
for ENTRY in "${COURT_IDS[@]}"; do
  CNAME="${ENTRY%%=*}"
  REST="${ENTRY#*=}"
  CID="${REST%%=*}"
  CALID="${REST#*=}"
  echo "   $CNAME → $CID  (calendar: $CALID)"
done
echo ""
echo " Next steps:"
echo "   1. Record the above IDs in docs/provisioning.md"
echo "   2. Verify webhook: curl \"https://api.telegram.org/bot$BOT_TOKEN/getWebhookInfo\""
echo "   3. Send a test message to the bot and confirm a reply"
echo "================================================================"
