#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ELASTICSEARCH_URL="${ELASTICSEARCH_URL:-http://localhost:9200}"
KIBANA_URL="${KIBANA_URL:-http://localhost:5601}"

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

create_data_view() {
    local name="$1"
    local title="$2"

    curl -fsS -X POST "$KIBANA_URL/api/data_views/data_view" \
        -H 'kbn-xsrf: true' \
        -H 'Content-Type: application/json' \
        -d "{\"data_view\":{\"name\":\"${name}\",\"title\":\"${title}\",\"timeFieldName\":\"@timestamp\"}}" \
        >/dev/null 2>&1 || true
}

setup_kibana() {
    wait_for_http Kibana "$KIBANA_URL/api/status"

    create_data_view "DevOpsStudy Logs" "devopsstudy-logs-*"
    create_data_view "DevOpsStudy Demo Metrics" "devopsstudy-metrics-*"
    create_data_view "DevOpsStudy Metricbeat" "devopsstudy-metricbeat-*"

    echo "Kibana data views are ready:"
    echo "- DevOpsStudy Logs"
    echo "- DevOpsStudy Demo Metrics"
    echo "- DevOpsStudy Metricbeat"
}

cd "$ROOT_DIR"

echo "Starting ELK stack..."
docker compose -f support/elk.yaml up -d

wait_for_http Elasticsearch "$ELASTICSEARCH_URL"
setup_kibana

echo
echo "============================================================"
echo "ELK SETUP IS READY"
echo "============================================================"
echo "NEXT:"
echo "1. Start GatewayService, OrderService, PaymentService, BankService."
echo "2. Run: ./support/post-setup.sh"
echo "3. Open Kibana: $KIBANA_URL"
echo "============================================================"
