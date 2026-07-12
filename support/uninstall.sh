#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT_DIR/support/logs"
ELASTICSEARCH_URL="${ELASTICSEARCH_URL:-http://localhost:9200}"
KIBANA_URL="${KIBANA_URL:-http://localhost:5601}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"

shutdown_spring_boot_services() {
    echo "Stopping running Spring Boot services through actuator shutdown..."

    local services=(
        "GatewayService|${GATEWAY_URL}"
        "OrderService|http://localhost:8081"
        "PaymentService|http://localhost:8082"
        "BankService|http://localhost:8083"
    )

    for service in "${services[@]}"; do
        local name="${service%%|*}"
        local base_url="${service#*|}"

        if ! curl -fsS "$base_url/actuator/health" >/dev/null 2>&1; then
            echo "${name} is not running, skipping."
            continue
        fi

        if curl -fsS -X POST "$base_url/actuator/shutdown" >/dev/null 2>&1; then
            echo "Shutdown signal sent to ${name}."
        else
            echo "Failed to call ${name} shutdown endpoint. Is actuator shutdown exposed?"
        fi
    done
}

delete_kibana_data_views() {
    if ! curl -fsS "$KIBANA_URL/api/status" >/dev/null 2>&1; then
        echo "Kibana is not reachable, skipping data view cleanup."
        return
    fi

    echo "Deleting Kibana data views created by setup.sh..."
    local titles=(
        "devopsstudy-logs-*"
        "devopsstudy-metrics-*"
        "devopsstudy-metricbeat-*"
    )

    for title in "${titles[@]}"; do
        local response
        local ids

        response="$(curl -fsS "$KIBANA_URL/api/saved_objects/_find?type=index-pattern&per_page=1000&search_fields=title&search=${title}" \
            -H 'kbn-xsrf: true' || true)"

        ids="$(sed -n 's/.*"saved_objects":\[\(.*\)\].*/\1/p' <<<"$response" \
            | tr '{' '\n' \
            | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')"

        if [[ -z "$ids" ]]; then
            echo "No Kibana data view found for ${title}."
            continue
        fi

        while IFS= read -r id; do
            [[ -z "$id" ]] && continue
            curl -fsS -X DELETE "$KIBANA_URL/api/saved_objects/index-pattern/${id}" \
                -H 'kbn-xsrf: true' >/dev/null 2>&1 || true
            echo "Deleted Kibana data view ${title} (${id})."
        done <<<"$ids"
    done
}

delete_elasticsearch_indices() {
    if ! curl -fsS "$ELASTICSEARCH_URL" >/dev/null 2>&1; then
        echo "Elasticsearch is not reachable, skipping index cleanup."
        return
    fi

    echo "Deleting Elasticsearch indices created by setup.sh..."
    curl -fsS -X DELETE \
        "$ELASTICSEARCH_URL/devopsstudy-logs-*,devopsstudy-metrics-*,devopsstudy-metricbeat-*?ignore_unavailable=true&expand_wildcards=open,closed" \
        >/dev/null 2>&1 || true
}

stop_elk_stack() {
    echo "Stopping ELK containers and removing Elasticsearch data volume..."
    docker compose -f "$ROOT_DIR/support/elk.yaml" down -v --remove-orphans || true
}

clean_setup_logs() {
    echo "Deleting local setup logs..."
    rm -rf "$LOG_DIR"
}

cd "$ROOT_DIR"

shutdown_spring_boot_services
delete_kibana_data_views
delete_elasticsearch_indices
stop_elk_stack
clean_setup_logs

echo "ELK setup has been uninstalled."
