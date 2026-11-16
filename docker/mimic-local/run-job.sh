#!/usr/bin/env bash
# Start (or dry-run) the MIMIC job through the Ignifyr REST API and print the execution id.
#
#   ./run-job.sh                              # all mapping tasks, results written (gzip NDJSON)
#   ./run-job.sh --skip-write                 # all mapping tasks, nothing written: metrics only
#   ./run-job.sh --skip-write patient-mapping admissions-mapping   # selected tasks (names from the job file)
#
# Environment: IGNIFYR_URL (default http://localhost:6085/ignifyr), PROJECT_ID (mimic), JOB_ID (mimic-hosp-csv-to-fhir-server)
set -euo pipefail
IGNIFYR_URL="${IGNIFYR_URL:-http://localhost:6085/ignifyr}"
PROJECT_ID="${PROJECT_ID:-mimic}"
JOB_ID="${JOB_ID:-mimic-hosp-csv-to-fhir-server}"

SKIP_WRITE=false
TASKS=()
for arg in "$@"; do
  case "$arg" in
    --skip-write) SKIP_WRITE=true ;;
    *) TASKS+=("$arg") ;;
  esac
done

if [ ${#TASKS[@]} -gt 0 ]; then
  TASK_JSON=$(printf '"%s",' "${TASKS[@]}"); TASK_JSON="[${TASK_JSON%,}]"
  BODY="{\"clearCheckpoints\": false, \"skipWrite\": ${SKIP_WRITE}, \"mappingTaskNames\": ${TASK_JSON}}"
else
  BODY="{\"clearCheckpoints\": false, \"skipWrite\": ${SKIP_WRITE}}"
fi

echo "POST ${IGNIFYR_URL}/projects/${PROJECT_ID}/jobs/${JOB_ID}/run  body=${BODY}"
# The server answers as soon as the execution has started. Server images built before 16 Nov 2026 answered only after
# the source of the first task had been read, so on big sources curl printed "408 Request Timeout" (or timed out)
# although the execution went on; with such an image, check the executions below.
curl -sS -m 120 -X POST "${IGNIFYR_URL}/projects/${PROJECT_ID}/jobs/${JOB_ID}/run" \
  -H 'Content-Type: application/json' -d "${BODY}" \
  || echo "(no answer from the POST: check the executions below; a server image older than 16 Nov 2026 times out here on big sources)"
echo
echo "Running executions:"
curl -sS "${IGNIFYR_URL}/projects/${PROJECT_ID}/jobs/${JOB_ID}/executions"
echo
echo "Stop with: curl -X DELETE ${IGNIFYR_URL}/projects/${PROJECT_ID}/jobs/${JOB_ID}/executions/<executionId>/stop"
