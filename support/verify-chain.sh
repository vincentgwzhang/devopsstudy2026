#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT_DIR/support/logs"
REQUEST_BODY='{"customerId":"customer-1001","amount":68.80,"currency":"USD"}'

mkdir -p "$LOG_DIR"
rm -f "$LOG_DIR"/*.log

pids=()

cleanup() {
    for pid in "${pids[@]:-}"; do
        if kill -0 "$pid" >/dev/null 2>&1; then
            kill "$pid" >/dev/null 2>&1 || true
        fi
    done
}
trap cleanup EXIT

start_service() {
    local module="$1"
    local log_file="$LOG_DIR/${module}.log"

    echo "Starting ${module}..."
    (cd "$ROOT_DIR" && mvn -q -pl "$module" spring-boot:run >"$log_file" 2>&1) &
    pids+=("$!")
}

wait_for_http() {
    local name="$1"
    local url="$2"
    local deadline=$((SECONDS + 90))

    until curl -fsS "$url" >/dev/null 2>&1; do
        if (( SECONDS >= deadline )); then
            echo "Timed out waiting for ${name}: ${url}"
            echo "Last log lines:"
            tail -80 "$LOG_DIR/${name}.log" || true
            exit 1
        fi
        sleep 1
    done
}

assert_log() {
    local module="$1"
    local expected="$2"
    local log_file="$LOG_DIR/${module}.log"
    local matched_line

    matched_line="$(grep -F "$expected" "$log_file" | tail -1 || true)"
    if [[ -z "$matched_line" ]]; then
        echo "Missing expected log in ${module}: ${expected}"
        tail -80 "$log_file" || true
        exit 1
    fi

    if ! grep -E '\[traceId=[^-][^ ]* spanId=[^-][^ ]* parentSpanId=' <<<"$matched_line" >/dev/null; then
        echo "Matched log line does not contain non-empty traceId/spanId in ${module}:"
        echo "$matched_line"
        exit 1
    fi

    echo "${module}: ${matched_line}"
}

cd "$ROOT_DIR"

echo "Building modules..."
mvn -q -DskipTests install

start_service BankService
start_service PaymentService
start_service OrderService
start_service GatewayService

wait_for_http BankService http://localhost:8083/actuator/health
wait_for_http PaymentService http://localhost:8082/actuator/health
wait_for_http OrderService http://localhost:8081/actuator/health
wait_for_http GatewayService http://localhost:8080/actuator/health

echo "Sending request through GatewayService..."
curl -fsS \
    -H 'Content-Type: application/json' \
    -d "$REQUEST_BODY" \
    http://localhost:8080/orders \
    > "$LOG_DIR/response.json"

echo
echo "Response:"
cat "$LOG_DIR/response.json"
echo

assert_log GatewayService "GatewayService forwarding request"
assert_log OrderService "OrderService received a calling"
assert_log PaymentService "PaymentService received a calling"
assert_log BankService "BankService received a calling"

echo
echo "Checking Zipkin API..."
sleep 1
if curl -fsS "http://localhost:9411/api/v2/services" > "$LOG_DIR/zipkin-services.json"; then
    if grep -E 'GatewayService|OrderService|PaymentService|BankService' "$LOG_DIR/zipkin-services.json" >/dev/null; then
        echo "Zipkin received traces:"
        cat "$LOG_DIR/zipkin-services.json"
        echo
    else
        echo "Zipkin is reachable, but expected service names are not present yet."
        cat "$LOG_DIR/zipkin-services.json"
        echo
        exit 1
    fi
else
    echo "Zipkin is not reachable at http://localhost:9411. Start it with:"
    echo "docker compose -f support/zipkin.yaml up -d"
    exit 1
fi

echo
echo "Trace log check passed. Logs are in support/logs:"
ls -1 "$LOG_DIR"
