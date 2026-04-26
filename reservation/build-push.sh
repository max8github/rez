#!/usr/bin/env bash
# Build and push the rez image, then optionally redeploy.
#
# Usage:
#   ./build-push.sh standalone          # build, push to Gitea, redeploy on lurch
#   ./build-push.sh cloud               # build, push to Akka registry, deploy to Akka Cloud
#   ./build-push.sh standalone --no-deploy
#   ./build-push.sh cloud    --no-deploy
set -euo pipefail

TARGET="${1:-standalone}"
DEPLOY=true
if [[ "${2:-}" == "--no-deploy" ]]; then
  DEPLOY=false
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

build_and_find_local_image() {
  local log_file
  log_file="$(mktemp)"

  "$@" | tee "$log_file"

  local local_image
  local_image=$(
    sed -n 's|.*Tagging image \(reservation:[^ ]*\) successful!.*|\1|p' "$log_file" | tail -n 1
  )
  rm -f "$log_file"

  if [[ -z "${local_image:-}" ]]; then
    echo "ERROR: could not find Maven-produced local image tag in build output" >&2
    exit 1
  fi

  printf '%s\n' "$local_image"
}

case "$TARGET" in
  standalone)
    REGISTRY="gitea-reg.fritz.box:3000/max/rez"
    TAG="${REZ_TAG:-latest}"
    IMAGE="${REGISTRY}:${TAG}"

    echo "==> [standalone] Building (mvn install -DskipTests -Pgoogle,standalone) ..."
    LOCAL_IMAGE=$(build_and_find_local_image mvn install -DskipTests -Pgoogle,standalone)
    echo "==> Using local image ${LOCAL_IMAGE}"

    echo "==> Tagging as ${IMAGE} ..."
    docker tag "$LOCAL_IMAGE" "$IMAGE"

    echo "==> Pushing to Gitea ..."
    docker push "$IMAGE"

    if [[ "$DEPLOY" == "true" ]]; then
      COMPOSE_SRC="$SCRIPT_DIR/../deploy/standalone/compose.yaml"
      echo "==> Syncing compose.yaml to lurch ..."
      scp "$COMPOSE_SRC" lurch:/tmp/rez-compose.yaml
      ssh lurch "pct push 115 /tmp/rez-compose.yaml /home/rez/compose.yaml && rm /tmp/rez-compose.yaml"

      echo "==> Redeploying on lurch ..."
      ssh lurch "pct exec 115 -- docker compose \
        --env-file /home/rez/.env \
        -f /home/rez/compose.yaml \
        pull rez && \
      pct exec 115 -- docker compose \
        --env-file /home/rez/.env \
        -f /home/rez/compose.yaml \
        up -d rez"
      echo "==> Tailing logs (Ctrl-C to stop):"
      ssh lurch "pct exec 115 -- docker logs rez -f --tail 50"
    fi
    ;;

  cloud)
    echo "==> [cloud] Building (mvn install -DskipTests --settings settings.xml -Pgoogle) ..."
    LOCAL_IMAGE=$(build_and_find_local_image mvn install -DskipTests --settings settings.xml -Pgoogle)
    echo "==> Using local image ${LOCAL_IMAGE}"

    if [[ "$DEPLOY" == "true" ]]; then
      echo "==> Pushing to Akka registry and deploying ..."
      akka service deploy rez "$LOCAL_IMAGE" --push --project rez-prod
      echo "==> Done: deployed ${LOCAL_IMAGE} to Akka Cloud"
    else
      echo "==> Pushing to Akka registry (no deploy) ..."
      akka container-registry push "$LOCAL_IMAGE" --project rez-prod
      echo "==> Done: pushed ${LOCAL_IMAGE} to Akka registry"
    fi
    ;;

  *)
    echo "Usage: $0 [standalone|cloud] [--no-deploy]"
    exit 1
    ;;
esac
