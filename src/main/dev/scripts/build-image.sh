#!/usr/bin/env bash
# Builds the application with Maven (classes + runtime dependencies in target/) and then the container image.
#   src/main/dev/scripts/build-image.sh [image-name]     default: localhost/reqlith:dev
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
IMAGE="${1:-localhost/reqlith:dev}"
ENGINE=$(command -v docker || command -v podman)
MAVEN=$(command -v mvnd || echo "$ROOT/mvnw")

cd "$ROOT"
"$MAVEN" -B -q -DskipTests -Djacoco.skip=true package
"$ENGINE" build -t "$IMAGE" -f src/main/docker/Dockerfile .
echo "Built $IMAGE"
