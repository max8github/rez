#!/usr/bin/env bash
# Build and push the rez image, then optionally redeploy.
#
# Usage:
#   ./build-push.sh standalone          # build, push to Gitea, redeploy on lurch
#   ./build-push.sh cloud               # build, push to Docker Hub, deploy to Akka Cloud
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

case "$TARGET" in
  standalone)
    REGISTRY="gitea-reg.fritz.box:3000/max/rez"
    TAG="${REZ_TAG:-latest}"
    IMAGE="${REGISTRY}:${TAG}"

    echo "==> [standalone] Building (mvn install -DskipTests -Pgoogle,standalone) ..."
    mvn install -DskipTests -Pgoogle,standalone

    echo "==> Tagging as ${IMAGE} ..."
    docker tag reservation:1.0 "$IMAGE"

    echo "==> Pushing to Gitea ..."
    docker push "$IMAGE"

    if [[ "$DEPLOY" == "true" ]]; then
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
    DOCKERHUB_IMAGE="max8github/rez"
    TAG="${REZ_TAG:-1.0}"
    IMAGE="${DOCKERHUB_IMAGE}:${TAG}"

    echo "==> [cloud] Building (mvn install -DskipTests --settings settings.xml -Pgoogle) ..."
    mvn install -DskipTests --settings settings.xml -Pgoogle

    echo "==> Tagging as ${IMAGE} ..."
    docker tag reservation:1.0 "$IMAGE"

    echo "==> Pushing to Docker Hub ..."
    docker push "$IMAGE"

    if [[ "$DEPLOY" == "true" ]]; then
      echo "==> Deploying to Akka Cloud ..."
      akka service deploy rez "$IMAGE" --project rez-prod
    fi
    ;;

  *)
    echo "Usage: $0 [standalone|cloud] [--no-deploy]"
    exit 1
    ;;
esac

echo "==> Done: ${IMAGE}"
