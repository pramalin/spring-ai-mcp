#!/usr/bin/env sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_dir"

compose() {
  docker compose "$@"
}

wait_for_health() {
  service="$1"
  attempts="${2:-90}"
  count=0

  while [ "$count" -lt "$attempts" ]; do
    container_id=$(compose ps -q "$service" 2>/dev/null | head -1 || true)
    if [ -n "$container_id" ]; then
      status=$(docker inspect \
        --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
        "$container_id" 2>/dev/null || true)
      case "$status" in
        healthy|running)
          return 0
          ;;
        unhealthy|exited|dead)
          echo "$service entered state: $status" >&2
          compose logs "$service" >&2 || true
          return 1
          ;;
      esac
    fi

    count=$((count + 1))
    sleep 1
  done

  echo "Timed out waiting for $service to become healthy." >&2
  compose logs "$service" >&2 || true
  return 1
}
