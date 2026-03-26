#!/usr/bin/env bash
# provision-facility.sh — Create a new Rez facility with its courts and register the Telegram webhook.
#
# Usage:
#   ./scripts/provision-facility.sh \
#     --host     https://maxdc.duckdns.org \
#     --name     "Erster Tennisclub Edingen-Neckarhausen" \
#     --street   "Mannheimer Str. 50" \
#     --city     "68535 Edingen-Neckarhausen" \
#     --timezone "Europe/Berlin" \
#     --token    "123456789:ABCdef..." \
#     --admins   "987654321,111222333" \
#     --courts   "Court 1:abc@group.calendar.google.com,Court 2:def@group.calendar.google.com"
#
# --courts is a comma-separated list of "name:calendarId" pairs.
# --admins is a comma-separated list of Telegram user IDs.
#
# The script prints all generated IDs at the end and suggests the next steps.
set -euo pipefail

# ---- parse arguments --------------------------------------------------------
HOST=""
NAME=""
STREET=""
CITY=""
TIMEZONE="Europe/Berlin"
BOT_TOKEN=""
ADMINS=""
COURTS=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host)     HOST="$2";     shift 2 ;;
    --name)     NAME="$2";     shift 2 ;;
    --street)   STREET="$2";   shift 2 ;;
    --city)     CITY="$2";     shift 2 ;;
    --timezone) TIMEZONE="$2"; shift 2 ;;
    --token)    BOT_TOKEN="$2"; shift 2 ;;
    --admins)   ADMINS="$2";   shift 2 ;;
    --courts)   COURTS="$2";   shift 2 ;;
    *) echo "Unknown argument: $1"; exit 1 ;;
  esac
done

# ---- validate ---------------------------------------------------------------
missing=()
[[ -z "$HOST" ]]      && missing+=("--host")
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

# ---- build adminUserIds JSON array ------------------------------------------
ADMIN_JSON="[]"
if [[ -n "$ADMINS" ]]; then
  ADMIN_JSON=$(echo "$ADMINS" | tr ',' '\n' | awk '{print "\"" $0 "\""}' | paste -sd ',' - | sed 's/^/[/;s/$/]/')
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
IFS=',' read -ra COURT_LIST <<< "$COURTS"
for ENTRY in "${COURT_LIST[@]}"; do
  COURT_NAME="${ENTRY%%:*}"
  CALENDAR_ID="${ENTRY#*:}"
  COURT_ID=$(curl -sf -X POST "$HOST/facility/$FACILITY_ID/resource" \
    -H "Content-Type: application/json" \
    -d "{
      \"name\": $(echo "$COURT_NAME" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read().strip()))'),
      \"calendarId\": \"$CALENDAR_ID\"
    }")
  echo "    $COURT_NAME → $COURT_ID  (calendar: $CALENDAR_ID)"
  COURT_IDS+=("$COURT_NAME=$COURT_ID")
done

# ---- 3. register Telegram webhook -------------------------------------------
echo ""
echo "==> Registering Telegram webhook"
WEBHOOK_URL="https://${HOST#https://}/telegram/$BOT_TOKEN/webhook"
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
  echo "   ${ENTRY%%=*} → ${ENTRY#*=}"
done
echo ""
echo " Next steps:"
echo "   1. Record the above IDs in docs/provisioning.md"
echo "   2. Verify webhook: curl \"https://api.telegram.org/bot$BOT_TOKEN/getWebhookInfo\""
echo "   3. Send a test message to the bot and confirm a reply"
echo "================================================================"
