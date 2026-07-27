#!/usr/bin/env bash
set -Eeuo pipefail

IMAGE="${1:-rendoc:ci}"
TOKEN="rendoc-ci-token-0123456789"
OUTPUT_DIR="$(mktemp -d)"
CID_FILE="$OUTPUT_DIR/container.cid"
CONTAINER_ID=""

cleanup() {
  if [[ -n "$CONTAINER_ID" ]]; then
    docker rm -f "$CONTAINER_ID" >/dev/null 2>&1 || true
  fi
  rm -rf "$OUTPUT_DIR"
}
trap cleanup EXIT

if ! docker run -d \
  --cidfile "$CID_FILE" \
  --read-only \
  --tmpfs /tmp:rw,noexec,nosuid,nodev,size=134217728,mode=1777 \
  --tmpfs /var/lib/rendoc:rw,noexec,nosuid,nodev,size=536870912,uid=10001,gid=10001,mode=0750 \
  --cap-drop ALL \
  --security-opt no-new-privileges:true \
  -e "RENDOC_API_TOKEN=${TOKEN}" \
  -p 127.0.0.1::8080 \
  "$IMAGE" >/dev/null; then
  if [[ -s "$CID_FILE" ]]; then
    CONTAINER_ID="$(< "$CID_FILE")"
  fi
  exit 1
fi
CONTAINER_ID="$(< "$CID_FILE")"
[[ -n "$CONTAINER_ID" ]]

PORT="$(docker inspect -f '{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}' "$CONTAINER_ID")"
ready=false
for _ in $(seq 1 90); do
  if curl --fail --silent --connect-timeout 2 --max-time 5 \
    "http://127.0.0.1:${PORT}/readyz" >/dev/null 2>&1; then
    ready=true
    break
  fi
  if [[ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER_ID")" != "true" ]]; then
    break
  fi
  sleep 1
done

if [[ "$ready" != "true" ]]; then
  docker logs "$CONTAINER_ID"
  exit 1
fi

public_metrics_status="$(curl --silent --connect-timeout 2 --max-time 10 \
  --output /dev/null --write-out '%{http_code}' \
  "http://127.0.0.1:${PORT}/actuator/prometheus")"
authenticated_metrics_status="$(curl --silent --connect-timeout 2 --max-time 10 \
  --output "$OUTPUT_DIR/metrics.txt" --write-out '%{http_code}' \
  -H "Authorization: Bearer ${TOKEN}" \
  "http://127.0.0.1:${PORT}/actuator/prometheus")"
[[ "$public_metrics_status" == "401" ]]
[[ "$authenticated_metrics_status" == "200" ]]

convert() {
  local fixture="$1"
  local source="$2"
  local content_type="$3"
  local output="$OUTPUT_DIR/${source}.pdf"
  local status

  status="$(curl --silent --show-error --connect-timeout 2 --max-time 150 \
    --output "$output" --write-out '%{http_code}' \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: ${content_type}" \
    --data-binary "@${fixture}" \
    "http://127.0.0.1:${PORT}/api/v1/convert?source=${source}&target=pdf")"

  [[ "$status" == "200" ]]
  file "$output" | grep -q 'PDF document'
  grep -a -q '%%EOF' "$output"
}

convert \
  src/integrationTest/resources/fixtures/sample.docx \
  docx \
  application/vnd.openxmlformats-officedocument.wordprocessingml.document
convert \
  src/integrationTest/resources/fixtures/sample.xlsx \
  xlsx \
  application/vnd.openxmlformats-officedocument.spreadsheetml.sheet

docker exec "$CONTAINER_ID" sh -c \
  'set -- /var/lib/rendoc/work/request-*; [ "$1" = "/var/lib/rendoc/work/request-*" ]'

printf 'Container smoke test passed: image=%s port=%s\n' "$IMAGE" "$PORT"
