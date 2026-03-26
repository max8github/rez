#!/usr/bin/env bash
set -euo pipefail
exec "$(dirname "$0")/reservation/build-push.sh" "$@"
