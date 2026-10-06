#!/bin/sh
#
# POSIX sh port of scripts/e2e.ps1.
#
# Runs the same container-backed E2E scenarios against an isolated Compose
# project with unique free host ports, prints a pass/fail table, collects
# service logs on failure, and removes only its own containers, network, and
# volumes.
#
# Usage:
#   sh scripts/e2e.sh
#   APP_PORT=18080 E2E_STUB_PORT=18081 sh scripts/e2e.sh
#
# Supported shells: POSIX sh on Git Bash/MSYS2 (Windows), Linux, or macOS
# with a native Docker CLI and Docker Compose v2.

set -u

script_dir=$(CDPATH= cd "$(dirname "$0")" >/dev/null 2>&1 && pwd)
repository_root=$(CDPATH= cd "$script_dir/.." >/dev/null 2>&1 && pwd)

case "$(uname -s 2>/dev/null || echo unknown)" in
MINGW* | MSYS* | CYGWIN*)
    # Docker is a native Windows binary here, so stop MSYS from rewriting
    # container-side POSIX paths such as /opt/kafka/bin/... or /v1/forecast.
    export MSYS_NO_PATHCONV=1
    export MSYS2_ARG_CONV_EXCL='*'
    ;;
esac

# Unlike the PowerShell runner, this script is its own process, so its
# environment overrides cannot leak into the caller's shell.
app_port="${APP_PORT:-}"
stub_port="${E2E_STUB_PORT:-}"
app_base_url=""
stub_base_url=""

failure_message=""
last_status=""
last_body=""
compose_output=""
result_count=0

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/weather-watch-e2e.XXXXXX")" || {
    echo "Unable to create a temporary working directory" >&2
    exit 1
}
project_name="weather-watch-e2e-$$-$(date +%s)"
run_id=$(printf '%06x' "$(( $(date +%s) % 16777216 ))")
http_body_file="$work_dir/http-body"
health_body_file="$work_dir/health-body"
detail_file="$work_dir/scenario-detail"
batch_ids_file="$work_dir/batch-ids"
results_file="$work_dir/results"
: >"$results_file"

fail() {
    failure_message="$*"
    if [ -n "$detail_file" ]; then
        printf '%s' "$*" >"$detail_file"
    fi
    return 1
}

# ---------------------------------------------------------------------------
# Host port discovery
# ---------------------------------------------------------------------------

find_free_port_powershell() {
    if command -v pwsh >/dev/null 2>&1; then
        powershell_cmd=pwsh
    elif command -v powershell.exe >/dev/null 2>&1; then
        powershell_cmd=powershell.exe
    else
        return 1
    fi
    "$powershell_cmd" -NoProfile -NonInteractive -Command '
$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
$listener.Start()
$port = $listener.LocalEndpoint.Port
$listener.Stop()
Write-Output $port' 2>/dev/null | tr -d '\r\n'
}

find_free_port_python() {
    if command -v python3 >/dev/null 2>&1; then
        python_cmd=python3
    elif command -v python >/dev/null 2>&1; then
        python_cmd=python
    else
        return 1
    fi
    "$python_cmd" -c 'import socket
sock = socket.socket()
sock.bind(("127.0.0.1", 0))
print(sock.getsockname()[1])
sock.close()' 2>/dev/null | tr -d '\r\n'
}

find_free_port() {
    port=""
    case "$(uname -s 2>/dev/null || echo unknown)" in
    MINGW* | MSYS* | CYGWIN*)
        port="$(find_free_port_powershell)" || port=""
        ;;
    esac
    if [ -z "$port" ]; then
        port="$(find_free_port_python)" || port=""
    fi
    if [ -z "$port" ]; then
        return 1
    fi
    printf '%s' "$port"
}

# ---------------------------------------------------------------------------
# Docker Compose
# ---------------------------------------------------------------------------

# Runs docker compose from the repository root and captures its combined
# output in compose_output. Failure is reported through fail().
run_compose() {
    if compose_output="$(cd "$repository_root" && docker compose --project-name "$project_name" --profile e2e "$@" 2>&1)"; then
        return 0
    else
        status=$?
    fi
    fail "docker compose $* failed with exit code $status"
    return 1
}

# Some calls mirror the PowerShell runner, which ignores the exit code.
run_compose_lenient() {
    (cd "$repository_root" && docker compose --project-name "$project_name" --profile e2e "$@" 2>&1) || true
}

get_sql() {
    run_compose exec -T postgres psql -U weather -d weather -tAc "$1" || return 1
    printf '%s' "$compose_output" | tr -d '\r\n'
}

# ---------------------------------------------------------------------------
# HTTP and JSON helpers
# ---------------------------------------------------------------------------

http_request() {
    request_method="$1"
    request_url="$2"
    request_body="${3-}"
    if [ -n "$request_body" ]; then
        request_status=$(curl --silent --show-error --output "$http_body_file" \
            --write-out '%{http_code}' --request "$request_method" --max-time 90 \
            --header 'Content-Type: application/json' --data "$request_body" "$request_url") || {
            fail "HTTP $request_method $request_url failed"
            return 1
        }
    else
        request_status=$(curl --silent --show-error --output "$http_body_file" \
            --write-out '%{http_code}' --request "$request_method" --max-time 90 \
            "$request_url") || {
            fail "HTTP $request_method $request_url failed"
            return 1
        }
    fi
    if [ -z "$request_status" ]; then
        fail "HTTP $request_method $request_url returned no status"
        return 1
    fi
    last_status="$request_status"
    last_body="$(cat "$http_body_file")"
}

json_string() {
    printf '%s' "$1" | tr -d '\r\n' |
        awk -v key="$2" '
            {
                pattern = "\"" key "\"[[:space:]]*:[[:space:]]*\""
                if (match($0, pattern)) {
                    value = substr($0, RSTART + RLENGTH)
                    sub(/".*/, "", value)
                    print value
                    exit
                }
            }
        '
}

json_number() {
    printf '%s' "$1" | tr -d '\r\n' |
        awk -v key="$2" '
            {
                pattern = "\"" key "\"[[:space:]]*:[[:space:]]*-?[0-9][0-9.]*"
                if (match($0, pattern)) {
                    value = substr($0, RSTART, RLENGTH)
                    sub(/^[^:]*:[[:space:]]*/, "", value)
                    print value
                    exit
                }
            }
        '
}

now_millis() {
    if command -v python3 >/dev/null 2>&1; then
        python3 -c 'import time; print(int(time.time() * 1000))'
    elif command -v python >/dev/null 2>&1; then
        python -c 'import time; print(int(time.time() * 1000))'
    elif command -v pwsh >/dev/null 2>&1; then
        pwsh -NoProfile -NonInteractive -Command '[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()'
    elif command -v powershell.exe >/dev/null 2>&1; then
        powershell.exe -NoProfile -NonInteractive -Command '[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()' | tr -d '\r\n'
    else
        printf '%s' "$(($(date +%s) * 1000))"
    fi
}

# ---------------------------------------------------------------------------
# Scenario support
# ---------------------------------------------------------------------------

wait_for() {
    wait_description="$1"
    wait_timeout="$2"
    shift 2
    wait_deadline=$(($(date +%s) + wait_timeout))
    while [ "$(date +%s)" -lt "$wait_deadline" ]; do
        if "$@"; then
            return 0
        fi
        sleep 1
    done
    fail "Timed out waiting for $wait_description"
}

run_scenario() {
    rs_name="$1"
    rs_body="$2"
    printf '%s\n' "--> $rs_name"
    : >"$detail_file"
    (set -e; "$rs_body")
    rs_status=$?
    if [ "$rs_status" -eq 0 ] && [ ! -s "$detail_file" ]; then
        rs_state=PASS
        rs_detail=""
    else
        rs_state=FAIL
        rs_detail=$(cat "$detail_file")
    fi
    printf '%s\t%s\t%s\n' "$rs_name" "$rs_state" "$rs_detail" >>"$results_file"
    result_count=$((result_count + 1))
}

create_location() {
    create_name="$1"
    create_latitude="$2"
    create_longitude="${3:-106.8}"
    http_request POST "$app_base_url/api/locations" \
        "$(printf '{"name":"%s","latitude":%s,"longitude":%s}' "$create_name" "$create_latitude" "$create_longitude")"
    [ "$last_status" = "201" ] || {
        fail "Create '$create_name' expected 201 but got $last_status: $last_body"
        return 1
    }
    json_number "$last_body" id
}

latitude_calls() {
    http_request POST "$stub_base_url/__admin/requests/count" \
        "$(printf '{"method":"GET","urlPath":"/v1/forecast","queryParameters":{"latitude":{"equalTo":"%s"}}}' "$1")"
    json_number "$last_body" count
}

add_stub_mapping() {
    http_request POST "$stub_base_url/__admin/mappings" "$1"
    [ "$last_status" = "201" ] || {
        fail "Stub mapping was not created: $last_body"
        return 1
    }
}

reset_stub() {
    http_request POST "$stub_base_url/__admin/mappings/reset"
}

snapshot_count() {
    get_sql "SELECT count(*) FROM forecast_snapshots WHERE location_id = $1"
}

snapshot_exists() {
    snapshot_exists_count="$(snapshot_count "$1")" || return 1
    [ -n "$snapshot_exists_count" ] && [ "$snapshot_exists_count" -gt 0 ]
}

wait_for_snapshot() {
    snapshot_location_id="$1"
    snapshot_timeout="${2:-150}"
    wait_for "snapshot for location $snapshot_location_id" "$snapshot_timeout" snapshot_exists "$snapshot_location_id"
}

stub_provider_failure() {
    printf '{"priority":1,"request":{"method":"GET","urlPath":"/v1/forecast","queryParameters":{"latitude":{"equalTo":"%s"}}},"response":{"status":500}}' "$1"
}

stub_provider_delay() {
    printf '{"priority":1,"request":{"method":"GET","urlPath":"/v1/forecast","queryParameters":{"latitude":{"equalTo":"%s"}}},"response":{"status":200,"fixedDelayMilliseconds":%s}}' "$1" "$2"
}

stub_transient_failure() {
    printf '{"priority":1,"scenarioName":"transient-%s","requiredScenarioState":"Started","newScenarioState":"recovered","request":{"method":"GET","urlPath":"/v1/forecast","queryParameters":{"latitude":{"equalTo":"%s"}}},"response":{"status":500}}' "$1" "$2"
}

stub_transient_success() {
    printf '{"priority":1,"scenarioName":"transient-%s","requiredScenarioState":"recovered","request":{"method":"GET","urlPath":"/v1/forecast","queryParameters":{"latitude":{"equalTo":"%s"}}},"response":{"status":200,"bodyFileName":"open-meteo-current.json","headers":{"Content-Type":"application/json"}}}' "$1" "$2"
}

# ---------------------------------------------------------------------------
# Readiness checks
# ---------------------------------------------------------------------------

stub_ready() {
    curl --silent --fail --output /dev/null --max-time 2 "$stub_base_url/__admin/mappings" 2>/dev/null
}

application_ready() {
    if ! readiness_status=$(curl --silent --output "$health_body_file" --write-out '%{http_code}' --max-time 2 \
        "$app_base_url/actuator/health/readiness" 2>/dev/null); then
        return 1
    fi
    [ "$readiness_status" = "200" ] || return 1
    [ "$(json_string "$(cat "$health_body_file")" status)" = "UP" ]
}

wait_for_application() {
    wait_for "application readiness" 120 application_ready
}

cache_invalidated() {
    cache_calls=""
    http_request GET "$app_base_url/api/locations/$1/forecast" || return 1
    cache_calls="$(latitude_calls 44.1)" || return 1
    [ -n "$cache_calls" ] && [ "$cache_calls" -gt "$2" ]
}

batch_run_completed() {
    batch_completed_count="$(get_sql "SELECT count(*) FROM batch_job_execution WHERE status = 'COMPLETED'")" || return 1
    [ -n "$batch_completed_count" ] && [ "$batch_completed_count" -gt "$1" ]
}

batch_run_failed() {
    batch_failed_count="$(get_sql "SELECT count(*) FROM batch_job_execution WHERE status = 'FAILED'")" || return 1
    [ -n "$batch_failed_count" ] && [ "$batch_failed_count" -gt "$1" ]
}

broker_recovered() {
    http_request POST "$app_base_url/api/locations" \
        "$(printf '{"name":"recovered-%s","latitude":70.2,"longitude":106.8}' "$run_id")" || return 1
    [ "$last_status" = "201" ] || return 1
    broker_created_id="$(json_number "$last_body" id)"
    http_request DELETE "$app_base_url/api/locations/$broker_created_id"
}

# ---------------------------------------------------------------------------
# Scenarios
# ---------------------------------------------------------------------------

scenario_crud() {
    id="$(create_location "crud-$run_id" 41.1)"

    http_request GET "$app_base_url/api/locations/$id"
    [ "$last_status" = "200" ] || fail "Read after create failed"
    [ "$(json_string "$last_body" name)" = "crud-$run_id" ] || fail "Read after create failed"

    http_request GET "$app_base_url/api/locations"
    [ "$last_status" = "200" ] || fail "List after create failed"
    case "$last_body" in *"crud-$run_id"*) ;; *) fail "Created location missing from list" ;; esac

    http_request PUT "$app_base_url/api/locations/$id" \
        "$(printf '{"name":"crud-%s-v2","latitude":41.2,"longitude":106.9}' "$run_id")"
    [ "$last_status" = "200" ] || fail "Update returned $last_status"

    http_request GET "$app_base_url/api/locations/$id"
    [ "$(json_string "$last_body" name)" = "crud-$run_id-v2" ] || fail "Update was not persisted"

    http_request DELETE "$app_base_url/api/locations/$id"
    [ "$last_status" = "200" ] || [ "$last_status" = "204" ] || fail "Delete returned $last_status"

    http_request GET "$app_base_url/api/locations/$id"
    [ "$last_status" = "404" ] || fail "Deleted location still readable"

    http_request GET "$app_base_url/api/locations/$id/forecast"
    [ "$last_status" = "404" ] || fail "Forecast for deleted location was not 404"
}

scenario_validation() {
    http_request POST "$app_base_url/api/locations" \
        "$(printf '{"name":"invalid-%s","latitude":95,"longitude":106.8}' "$run_id")"
    [ "$last_status" = "400" ] || fail "Expected 400 but got $last_status"

    http_request GET "$app_base_url/api/locations"
    case "$last_body" in *"invalid-$run_id"*) fail "Invalid location was stored" ;; esac
}

scenario_cache_reuse() {
    id="$(create_location "cache-$run_id" 42.1)"

    http_request GET "$app_base_url/api/locations/$id/forecast"
    first_status="$last_status"
    first_body="$last_body"
    calls_after_first="$(latitude_calls 42.1)"

    http_request GET "$app_base_url/api/locations/$id/forecast"
    second_status="$last_status"
    calls_after_second="$(latitude_calls 42.1)"

    [ "$first_status" = "200" ] && [ "$second_status" = "200" ] || fail "Forecast did not return 200"
    [ -n "$calls_after_first" ] && [ "$calls_after_first" -ge 1 ] || fail "First forecast did not call the provider"
    [ "$calls_after_second" = "$calls_after_first" ] || fail "Second forecast called the provider again"
    [ "$(json_number "$first_body" temperature2m)" = "$(json_number "$last_body" temperature2m)" ] ||
        fail "Cached payload differs"

    http_request DELETE "$app_base_url/api/locations/$id"
}

scenario_cache_ttl() {
    id="$(create_location "ttl-$run_id" 43.1)"
    http_request GET "$app_base_url/api/locations/$id/forecast"
    before="$(latitude_calls 43.1)"
    sleep 12
    http_request GET "$app_base_url/api/locations/$id/forecast"
    [ "$last_status" = "200" ] || fail "Forecast after TTL returned $last_status"
    after="$(latitude_calls 43.1)"
    [ -n "$after" ] && [ "$after" -gt "$before" ] || fail "Cache entry did not expire"
    http_request DELETE "$app_base_url/api/locations/$id"
}

scenario_kafka_invalidation() {
    id="$(create_location "inval-$run_id" 44.1)"
    http_request GET "$app_base_url/api/locations/$id/forecast"
    before="$(latitude_calls 44.1)"

    http_request PUT "$app_base_url/api/locations/$id" \
        "$(printf '{"name":"inval-%s","latitude":44.1,"longitude":106.9}' "$run_id")"
    [ "$last_status" = "200" ] || fail "Update failed"

    wait_for "cache invalidation" 8 cache_invalidated "$id" "$before"
    http_request DELETE "$app_base_url/api/locations/$id"
}

scenario_kafka_repeated_events() {
    id="$(create_location "repeat-$run_id" 45.1)"
    http_request GET "$app_base_url/api/locations/$id/forecast"

    for attempt in 1 2 3; do
        http_request PUT "$app_base_url/api/locations/$id" \
            "$(printf '{"name":"repeat-%s","latitude":45.1,"longitude":106.8}' "$run_id")"
        [ "$last_status" = "200" ] || fail "Update failed"
    done

    sleep 3
    before_refill="$(latitude_calls 45.1)"
    http_request GET "$app_base_url/api/locations/$id/forecast"
    [ "$last_status" = "200" ] || fail "Forecast failed after repeated events"
    after_refill="$(latitude_calls 45.1)"
    http_request GET "$app_base_url/api/locations/$id/forecast"
    [ "$last_status" = "200" ] || fail "Forecast failed after refill"

    refill_delta=$((after_refill - before_refill))
    [ "$refill_delta" = "1" ] || fail "Expected one refill call, saw $refill_delta"
    final_calls="$(latitude_calls 45.1)"
    [ "$final_calls" = "$after_refill" ] || fail "Cache was not reused after refill"

    http_request DELETE "$app_base_url/api/locations/$id"
}

scenario_kafka_keyed_events() {
    id="$(create_location "events-$run_id" 46.1)"
    http_request PUT "$app_base_url/api/locations/$id" \
        "$(printf '{"name":"events-%s","latitude":46.1,"longitude":106.9}' "$run_id")"
    http_request DELETE "$app_base_url/api/locations/$id"

    lines="$(run_compose_lenient exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh \
        --bootstrap-server kafka:9092 --topic location-events --from-beginning --timeout-ms 10000 \
        --property print.key=true)"
    printf '%s\n' "$lines" >"$work_dir/kafka-events"
    awk -F '\t' -v event_key="$id" '$1 == event_key { print $2 }' \
        "$work_dir/kafka-events" >"$work_dir/kafka-payloads"
    event_joined=""
    while IFS= read -r event_payload; do
        event_type="$(json_string "$event_payload" eventType)"
        if [ -n "$event_joined" ]; then
            event_joined="$event_joined,"
        fi
        event_joined="$event_joined$event_type"
    done <"$work_dir/kafka-payloads"
    if [ "$event_joined" != "CREATED,UPDATED,DELETED" ]; then
        fail "Unexpected events for key $id: $event_joined"
    fi
}

scenario_provider_failure() {
    add_stub_mapping "$(stub_provider_failure 47.1)"
    id="$(create_location "pfail-$run_id" 47.1)"

    http_request GET "$app_base_url/api/locations/$id/forecast"
    [ "$last_status" = "502" ] || fail "Expected 502 on provider failure"

    reset_stub
    calls_before="$(latitude_calls 47.1)"
    http_request GET "$app_base_url/api/locations/$id/forecast"
    [ "$last_status" = "200" ] || fail "Forecast did not recover"
    [ "$(latitude_calls 47.1)" -gt "$calls_before" ] || fail "Failure response appears to have been cached"

    http_request DELETE "$app_base_url/api/locations/$id"
}

scenario_provider_timeout() {
    add_stub_mapping "$(stub_provider_delay 48.1 6000)"
    id="$(create_location "timeout-$run_id" 48.1)"

    start_ms="$(now_millis)"
    http_request GET "$app_base_url/api/locations/$id/forecast"
    forecast_status="$last_status"
    elapsed_ms=$(($(now_millis) - start_ms))
    reset_stub

    [ "$forecast_status" = "502" ] || fail "Expected 502 on timeout but got $forecast_status"
    [ "$elapsed_ms" -lt 5500 ] || fail "Read timeout was not enforced ($((elapsed_ms / 1000))s)"

    http_request DELETE "$app_base_url/api/locations/$id"
}

scenario_batch_partitioned() {
    : >"$batch_ids_file"
    for attempt in 1 2 3 4 5 6 7; do
        latitude="50.$attempt"
        location_id="$(create_location "batch-$run_id-$attempt" "$latitude")"
        printf '%s\n' "$location_id" >>"$batch_ids_file"
        wait_for_snapshot "$location_id"
    done

    bad_steps="$(get_sql "SELECT count(*) FROM (
  SELECT e.job_execution_id FROM batch_job_execution e
  JOIN batch_step_execution s ON s.job_execution_id = e.job_execution_id AND s.step_name LIKE 'forecastRefreshWorkerStep%'
  WHERE e.status = 'COMPLETED' GROUP BY e.job_execution_id HAVING count(*) > 3) t")"
    [ "$bad_steps" = "0" ] || fail "A completed job used more worker partitions than the grid size"

    completed="$(get_sql "SELECT count(*) FROM batch_job_execution WHERE status = 'COMPLETED'")"
    [ -n "$completed" ] && [ "$completed" -ge 1 ] || fail "No completed batch execution recorded"

    full="$(get_sql "SELECT count(*) FROM (
  SELECT e.job_execution_id FROM batch_job_execution e
  JOIN batch_step_execution s ON s.job_execution_id = e.job_execution_id AND s.step_name LIKE 'forecastRefreshWorkerStep%'
  WHERE e.status = 'COMPLETED' GROUP BY e.job_execution_id HAVING count(*) = 3) t")"
    [ -n "$full" ] && [ "$full" -ge 1 ] || fail "No completed job fanned out into the 3 configured worker partitions"
}

scenario_batch_repeat_runs() {
    probe="$(head -n 1 "$batch_ids_file")"
    [ -n "$probe" ] || fail "Batch partitioning scenario did not create a location"

    runs_now="$(get_sql "SELECT count(*) FROM batch_job_execution WHERE status = 'COMPLETED'")"
    wait_for "another completed run" 120 batch_run_completed "$runs_now"

    [ "$(snapshot_count "$probe")" = "1" ] || fail "Snapshot count for a location is not 1 after repeated runs"

    duplicates="$(get_sql "SELECT count(*) FROM (SELECT 1 FROM forecast_snapshots GROUP BY location_id, forecast_time HAVING count(*) > 1) d")"
    [ "$duplicates" = "0" ] || fail "Duplicate snapshots found"
}

scenario_batch_non_overlapping() {
    overlaps="$(get_sql "SELECT count(*) FROM batch_job_execution a JOIN batch_job_execution b
  ON a.job_execution_id < b.job_execution_id AND a.start_time < b.end_time AND b.start_time < a.end_time")"
    [ "$overlaps" = "0" ] || fail "Overlapping batch job executions detected"
}

scenario_batch_transient_retry() {
    add_stub_mapping "$(stub_transient_failure "$run_id" 60.1)"
    add_stub_mapping "$(stub_transient_success "$run_id" 60.1)"
    id="$(create_location "transient-$run_id" 60.1)"

    wait_for_snapshot "$id"
    [ "$(latitude_calls 60.1)" -ge 2 ] || fail "Provider was not retried"

    reset_stub
    http_request DELETE "$app_base_url/api/locations/$id"
}

scenario_batch_permanent_failure() {
    add_stub_mapping "$(stub_provider_failure 61.1)"
    before="$(get_sql "SELECT count(*) FROM batch_job_execution WHERE status = 'FAILED'")"
    id="$(create_location "permanent-$run_id" 61.1)"

    wait_for "FAILED batch execution" 120 batch_run_failed "$before"
    [ "$(snapshot_count "$id")" = "0" ] || fail "Failed location unexpectedly has a snapshot"

    reset_stub
    wait_for_snapshot "$id"
    http_request DELETE "$app_base_url/api/locations/$id"
}

scenario_kafka_outage() {
    run_compose stop kafka || fail "Unable to stop the Kafka container"

    # The broker is restarted no matter how the outage checks below turn out.
    if http_request POST "$app_base_url/api/locations" \
        "$(printf '{"name":"outage-%s","latitude":70.1,"longitude":106.8}' "$run_id")"; then
        post_status="$last_status"
    else
        post_status="unavailable"
    fi
    if http_request GET "$app_base_url/api/locations"; then
        list_body="$last_body"
    else
        list_body=""
    fi
    run_compose start kafka || fail "Unable to start the Kafka container"

    [ "$post_status" = "503" ] || fail "Expected 503 during broker outage but got $post_status"
    case "$list_body" in *"outage-$run_id"*) fail "Location persisted despite publish failure" ;; esac

    wait_for "writes after broker recovery" 90 broker_recovered
}

# ---------------------------------------------------------------------------
# Runner
# ---------------------------------------------------------------------------

print_results() {
    result_name_width=$(awk -F '\t' 'BEGIN { width = 8 } length($1) > width { width = length($1) } END { print width }' "$results_file")
    result_failed_count=$(awk -F '\t' '$2 == "FAIL" { failed++ } END { print failed + 0 }' "$results_file")
    awk -F '\t' -v width="$result_name_width" '
        BEGIN {
            printf "%-*s %-6s %s\n", width, "Scenario", "Result", "Detail"
            printf "%-*s %-6s %s\n", width, "--------", "------", "------"
        }
        { printf "%-*s %-6s %s\n", width, $1, $2, $3 }
    ' "$results_file"

    if [ "$result_failed_count" -gt 0 ]; then
        fail "$result_failed_count of $result_count E2E scenarios failed"
        return 1
    fi
    printf '%s\n' "E2E passed: all $result_count container scenarios."
}

collect_logs() {
    if run_compose logs --no-color app postgres redis kafka open-meteo-stub; then
        printf '%s\n' "$compose_output"
    else
        printf '%s\n' "Warning: unable to collect Compose logs: $failure_message"
    fi
}

teardown() {
    if run_compose down --volumes --remove-orphans; then
        printf '%s\n' "Removed isolated E2E stack and its dedicated volumes."
        return 0
    fi
    printf '%s\n' "Warning: E2E cleanup failed; remove only project '$project_name' with: docker compose --project-name $project_name --profile e2e down --volumes --remove-orphans"
    return 1
}

bootstrap() {
    if [ -z "$app_port" ]; then
        app_port="$(find_free_port)" || app_port=""
        if [ -z "$app_port" ]; then
            fail "Unable to determine a free application port; set APP_PORT to an unused port"
            return 1
        fi
    fi
    if [ -z "$stub_port" ]; then
        stub_port="$(find_free_port)" || stub_port=""
        if [ -z "$stub_port" ]; then
            fail "Unable to determine a free stub port; set E2E_STUB_PORT to an unused port"
            return 1
        fi
    fi
    if [ "$app_port" = "$stub_port" ]; then
        fail "APP_PORT and E2E_STUB_PORT must differ (both are $app_port)"
        return 1
    fi

    app_base_url="http://127.0.0.1:$app_port"
    stub_base_url="http://127.0.0.1:$stub_port"

    export APP_PORT="$app_port"
    export E2E_STUB_PORT="$stub_port"
    export OPEN_METEO_BASE_URL="http://open-meteo-stub:8080"
    export WEATHER_BATCH_FIXED_DELAY="PT30S"
    export WEATHER_BATCH_GRID_SIZE="3"
    export WEATHER_BATCH_MAX_WORKERS="2"
    export WEATHER_BATCH_CHUNK_SIZE="2"
    export WEATHER_BATCH_RETRY_LIMIT="2"
    export WEATHER_BATCH_ENABLED="true"
    export FORECAST_CACHE_TTL="PT10S"
    export OPEN_METEO_READ_TIMEOUT="2s"
}

main() {
    printf '%s\n' "Starting isolated Compose project '$project_name' on port $app_port"
    run_compose up --build -d || return 1

    wait_for "Open-Meteo stub" 120 stub_ready || return 1
    wait_for_application || return 1

    run_scenario "CRUD: create, read, list, update, delete, then 404" scenario_crud
    run_scenario "Validation: invalid coordinates return 400 and store nothing" scenario_validation
    run_scenario "Cache: miss then reuse, with matching payloads" scenario_cache_reuse
    run_scenario "Cache: TTL expiry causes a new provider call" scenario_cache_ttl
    run_scenario "Kafka: update invalidates the cached forecast" scenario_kafka_invalidation
    run_scenario "Kafka: repeated events are harmless and the cache refills once" scenario_kafka_repeated_events
    run_scenario "Kafka: keyed CREATED, UPDATED, DELETED events are published in order" scenario_kafka_keyed_events
    run_scenario "Provider failure: explicit 502 and nothing cached" scenario_provider_failure
    run_scenario "Provider timeout: bounded 502 instead of hanging" scenario_provider_timeout
    run_scenario "Batch: partitioned scheduled run snapshots every location" scenario_batch_partitioned
    run_scenario "Batch: repeat runs update snapshots instead of duplicating them" scenario_batch_repeat_runs
    run_scenario "Batch: scheduled runs never overlap" scenario_batch_non_overlapping
    run_scenario "Batch: transient provider failure is retried and completes" scenario_batch_transient_retry
    run_scenario "Batch: permanent failure fails the job visibly, then recovers" scenario_batch_permanent_failure
    run_scenario "Kafka outage: write returns 503, rolls back, and service recovers" scenario_kafka_outage

    print_results
}

entry_point() {
    exit_code=0

    if ! bootstrap; then
        printf '%s\n' "E2E failed: ${failure_message:-unknown failure}"
        rm -rf "$work_dir"
        return 1
    fi

    main || exit_code=$?

    if [ "$exit_code" -ne 0 ]; then
        printf '%s\n' "E2E failed: ${failure_message:-unknown failure}"
        collect_logs
    fi

    if ! teardown; then
        if [ "$exit_code" -eq 0 ]; then
            exit_code=1
        fi
    fi

    rm -rf "$work_dir"
    return "$exit_code"
}

entry_point
exit $?
