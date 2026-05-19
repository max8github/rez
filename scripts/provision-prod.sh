#!/usr/bin/env bash
# provision-prod.sh — re-provision all production facilities on Akka Cloud.
#
# Run this after a full wipe-and-redeploy of the Rez service.
# Bot tokens are read from ~/.rez-prod.env (never committed to the repo).
#
# ~/.rez-prod.env format:
#   ETC_EN_BOT_TOKEN=123456789:ABCdef...
#   CTC_BOT_TOKEN=987654321:XYZghi...
#   ETC_EPP_BOT_TOKEN=111222333:MNOpqr...
#
# Usage:
#   ./scripts/provision-prod.sh
#   HOST=https://other-host.akka.services ./scripts/provision-prod.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SECRETS_FILE="$(cd "$SCRIPT_DIR/.." && pwd)/.env.production"
if [ ! -f "$SECRETS_FILE" ]; then
  echo "ERROR: $SECRETS_FILE not found."
  echo ""
  echo "Create it with:"
  echo "  ETC_EN_BOT_TOKEN=<token>"
  echo "  CTC_BOT_TOKEN=<token>"
  echo "  ETC_EPP_BOT_TOKEN=<token>"
  exit 1
fi

set -a && source "$SECRETS_FILE" && set +a

: "${ETC_EN_BOT_TOKEN:?Missing ETC_EN_BOT_TOKEN in $SECRETS_FILE}"
: "${CTC_BOT_TOKEN:?Missing CTC_BOT_TOKEN in $SECRETS_FILE}"
: "${ETC_EPP_BOT_TOKEN:?Missing ETC_EPP_BOT_TOKEN in $SECRETS_FILE}"

HOST="${HOST:-https://red-shadow-4568.europe-west1.akka.services}"

echo ""
echo "Host: $HOST"
echo ""

# ── 1. ETC Edingen ────────────────────────────────────────────────────────────
echo "=== Provisioning ETC Edingen-Neckarhausen ==="
"$SCRIPT_DIR/provision-facility.sh" \
  --host "$HOST" \
  --webhook-host "$HOST" \
  --name "Erster Tennisclub Edingen-Neckarhausen" \
  --street "Mannheimer Str. 50" \
  --city "68535 Edingen-Neckarhausen" \
  --timezone Europe/Berlin \
  --token "$ETC_EN_BOT_TOKEN" \
  --courts "Court 1:3d228lvsdmdjmj79662t8r1fh4@group.calendar.google.com,Court 2:63hd39cd9ppt8tajp76vglt394@group.calendar.google.com,Court 3:42cf1e8db6c37f2a7c8f02dbf9b6fc9d497008ecd92a30892ea7b1a380c8e130@group.calendar.google.com,Court 4:2bba1d7802c29ab3a4455cadaebc68b0bf79370ac009b053664c9a2decb2ea1a@group.calendar.google.com"

echo ""

# ── 2. CTC Cittadellese ───────────────────────────────────────────────────────
echo "=== Provisioning CTC Circolo Tennistico Cittadellese ==="
"$SCRIPT_DIR/provision-facility.sh" \
  --host "$HOST" \
  --webhook-host "$HOST" \
  --name "Circolo Tennistico Cittadellese" \
  --street "Via Giovanni XXIII, 30" \
  --city "35014 Fontaniva PD" \
  --timezone Europe/Rome \
  --token "$CTC_BOT_TOKEN" \
  --courts "Campo 1:a2f154caafd74dc43fb4a6a6a04542fb8e0fceba7e3bb129a10c6669a6d1023a@group.calendar.google.com,Campo 2:2c5b30f692e92bd385c60b08fa58919683a0a4b54fb0d9bc3568c902d29c1776@group.calendar.google.com,Campo 3:dc47389291f68cda93211cffacf90faa98efaee5b7784657000e33218d4efd96@group.calendar.google.com,Campo 4:40f2bf51f1ea9727c89f561f41c8299675ffd5b2b83d10c2699d8768746fca6f@group.calendar.google.com"

echo ""

# ── 3. Eppelheimer Tennis-Club ────────────────────────────────────────────────
echo "=== Provisioning Eppelheimer Tennis-Club ==="
"$SCRIPT_DIR/provision-facility.sh" \
  --host "$HOST" \
  --webhook-host "$HOST" \
  --name "Eppelheimer Tennis-Club e.V." \
  --street "Peter-Böhm-Straße 50" \
  --city "69214 Eppelheim" \
  --timezone Europe/Berlin \
  --token "$ETC_EPP_BOT_TOKEN" \
  --courts "Court 1:d5088961164845432bbe2f9a5e211cb4cc2461f4ef123840b2702e002166df7a@group.calendar.google.com,Court 2:b4077bbda87f48d89f808a557ecae67c7535c5c2de7a89e52f28e3b416df2547@group.calendar.google.com"

echo ""
echo "════════════════════════════════════════════════════════════"
echo "  All facilities provisioned. Update provisioning.md with"
echo "  the new facility IDs printed above."
echo "════════════════════════════════════════════════════════════"
