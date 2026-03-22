#!/usr/bin/env bash
# smoke-local.sh — end-to-end HTTP smoke test against a locally running Rez instance.
#
# Usage:
#   cd reservation/reservation && mvn compile exec:java -Plocal &
#   ./scripts/smoke-local.sh
#
# Steps 1-4  verify provisioning HTTP plumbing (always run).
# Steps 5-7  verify the Telegram → agent → booking flow.
#            These only produce a meaningful result when OPENAI_API_KEY is set and
#            the real LLM is reachable.  Without it, the agent won't complete and
#            step 7 will report SKIP instead of PASS/FAIL.
#
# Exit codes: 0 = all checked steps passed, 1 = one or more failures.

set -euo pipefail

BASE_URL="${REZ_BASE_URL:-http://localhost:9000}"
# Unique per run so FacilityByBotTokenView always resolves to this run's facility.
BOT_TOKEN="bot:smoke-$(openssl rand -hex 4)"
# Random date 1–85 days out — stays within ResourceState's 3-month booking window.
# Wide enough that repeated runs rarely collide on the same court slot.
BOOK_DATE=$(date -v "+$((RANDOM % 85 + 1))d" +%Y-%m-%d)
PASS=0
FAIL=0
SKIP=0

# ---- helpers ----------------------------------------------------------------

pass() { echo "  PASS: $1"; PASS=$((PASS + 1)); }
fail() { echo "  FAIL: $1"; FAIL=$((FAIL + 1)); }
skip() { echo "  SKIP: $1"; SKIP=$((SKIP + 1)); }

check_http() {
  local label="$1" expected="$2" actual="$3"
  if [[ "$actual" == "$expected" ]]; then pass "$label"; else fail "$label (expected $expected, got $actual)"; fi
}

# ---- step 1: health check ---------------------------------------------------

echo ""
echo "=== Step 1: health check ==="
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/facility/nonexistent-health-check" || echo "000")
if [[ "$STATUS" == "200" || "$STATUS" == "404" || "$STATUS" == "400" ]]; then
  pass "server is reachable (HTTP $STATUS)"
else
  fail "server unreachable or unexpected status: $STATUS"
  echo "Aborting — is 'mvn compile exec:java -Plocal' running?"
  exit 1
fi

# ---- step 2: create facility ------------------------------------------------

echo ""
echo "=== Step 2: create facility ==="
FACILITY_ID=$(curl -s -X POST "$BASE_URL/facility" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"Smoke Club\",
    \"address\": {\"street\": \"Smoke St 1\", \"city\": \"12345 Smoke\"},
    \"timezone\": \"Europe/Berlin\",
    \"botToken\": \"$BOT_TOKEN\",
    \"adminUserIds\": [\"123456\"]
  }")

if [[ -n "$FACILITY_ID" && "$FACILITY_ID" != "null" ]]; then
  pass "facility created: $FACILITY_ID"
else
  fail "facility creation returned empty/null response"
  exit 1
fi

# ---- step 3: create court ---------------------------------------------------

echo ""
echo "=== Step 3: create court ==="
COURT_ID=$(curl -s -X POST "$BASE_URL/facility/$FACILITY_ID/resource" \
  -H "Content-Type: application/json" \
  -d '{"name": "Smoke Court 1", "calendarId": "smoke-cal@group.calendar.google.com"}')

if [[ -n "$COURT_ID" && "$COURT_ID" != "null" ]]; then
  pass "court created: $COURT_ID"
else
  fail "court creation returned empty/null response"
  exit 1
fi

# ---- step 4: wait for async registration chain to complete ------------------

echo ""
echo "=== Step 4: wait for court registration ==="
# FacilityEntity::registerResource is the last step in the async chain
# (FacilityEntity → FacilityAction → ResourceEntity → FacilityEntity).
# Polling GET /facility until $COURT_ID appears in resourceIds is the reliable signal
# that the ResourceView is also ready for checkAvailability.
FOUND=false
for i in $(seq 1 30); do
  FACILITY_JSON=$(curl -s "$BASE_URL/facility/$FACILITY_ID" || true)
  if echo "$FACILITY_JSON" | grep -q "$COURT_ID"; then
    FOUND=true
    break
  fi
  sleep 0.5
done

if $FOUND; then
  pass "court registered in facility (async chain complete)"
else
  fail "court not registered in facility after 15 s"
fi

# ---- step 5: send Telegram webhook ------------------------------------------

echo ""
echo "=== Step 5: send Telegram booking message ==="
WEBHOOK_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "$BASE_URL/telegram/$BOT_TOKEN/webhook" \
  -H "Content-Type: application/json" \
  -d "{
    \"message\": {
      \"message_id\": 1,
      \"from\": {\"id\": 123456, \"first_name\": \"Max\", \"username\": \"max\"},
      \"chat\": {\"id\": 123456, \"type\": \"private\"},
      \"text\": \"Book a court for $BOOK_DATE at 10:00 for Max\"
    }
  }")

check_http "webhook returns 200" "200" "$WEBHOOK_STATUS"

# ---- step 6: wait for agent + booking to complete ---------------------------

echo ""
echo "=== Step 6: waiting for agent response (5 s) ==="
sleep 5

# ---- step 7: verify booking (only with real LLM) ----------------------------

echo ""
echo "=== Step 7: check court timeWindow for booking ==="
# Requires OPENAI_API_KEY to be set in the *server's* environment.
# If the agent didn't complete (no key on the server), this step will fail.
RESOURCE_JSON=$(curl -s "$BASE_URL/resource/$COURT_ID")
if echo "$RESOURCE_JSON" | grep -q "$BOOK_DATE"; then
  pass "court timeWindow contains the booked slot"
else
  fail "court timeWindow does not contain $BOOK_DATE — booking may not have completed (is OPENAI_API_KEY set on the server?)"
  echo "       Resource state: $RESOURCE_JSON"
fi

# ---- summary ----------------------------------------------------------------

echo ""
echo "================================================"
echo "  Results: PASS=$PASS  FAIL=$FAIL  SKIP=$SKIP"
echo "================================================"

if [[ $FAIL -gt 0 ]]; then exit 1; fi
