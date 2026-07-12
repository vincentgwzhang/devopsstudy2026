#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT_DIR/support/logs"
ELASTICSEARCH_URL="${ELASTICSEARCH_URL:-http://localhost:9200}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
GATEWAY_ORDER_PATH="${GATEWAY_ORDER_PATH:-/orders}"
REQUEST_BODY='{"customerId":"customer-1001","amount":68.80,"currency":"USD"}'

mkdir -p "$LOG_DIR"

wait_for_http() {
    local name="$1"
    local url="$2"
    local deadline=$((SECONDS + 120))

    until curl -fsS "$url" >/dev/null 2>&1; do
        if (( SECONDS >= deadline )); then
            echo "Timed out waiting for ${name}: ${url}"
            exit 1
        fi
        sleep 2
    done
}

assert_es_count() {
    local name="$1"
    local pattern="$2"
    local count

    count="$(curl -fsS "$ELASTICSEARCH_URL/${pattern}/_count?ignore_unavailable=true" \
        | sed -n 's/.*"count":\([0-9][0-9]*\).*/\1/p')"

    if [[ -z "$count" || "$count" == "0" ]]; then
        echo "Missing Elasticsearch documents for ${name}: ${pattern}"
        echo "Known indices:"
        curl -fsS "$ELASTICSEARCH_URL/_cat/indices/devopsstudy-*?v" || true
        exit 1
    fi

    echo "${name}: ${count} documents in ${pattern}"
}

cd "$ROOT_DIR"

echo "Waiting for ELK and manually started services..."
wait_for_http Elasticsearch "$ELASTICSEARCH_URL"
wait_for_http BankService http://localhost:8083/actuator/health
wait_for_http PaymentService http://localhost:8082/actuator/health
wait_for_http OrderService http://localhost:8081/actuator/health
wait_for_http GatewayService "$GATEWAY_URL/actuator/health"

echo "Sending request through GatewayService entrypoint..."
curl -fsS \
    -H 'Content-Type: application/json' \
    -d "$REQUEST_BODY" \
    "$GATEWAY_URL$GATEWAY_ORDER_PATH" \
    > "$LOG_DIR/response.json"

echo
echo "Response:"
cat "$LOG_DIR/response.json"
echo

echo
echo "Waiting for Logstash and Metricbeat to index documents..."
sleep 25

assert_es_count "Application logs" "devopsstudy-logs-*"
assert_es_count "Demo metrics" "devopsstudy-metrics-*"
assert_es_count "Metricbeat metrics" "devopsstudy-metricbeat-*"

echo
echo "Post setup verification passed. Response is in support/logs/response.json"
