#!/usr/bin/env bash
# Runs the given mapping tasks one after another, each as its own execution, and waits for each to finish.
# Restart-safe: if the machine or the server dies, the tasks that completed keep their events in Elasticsearch and
# you simply call the script again with the remaining task names. Meant to run detached on a server:
#
#   nohup data-ingestion-suite/docker/mimic-local/run-tasks.sh --skip-write labevents-mapping emar-mapping \
#         prescriptions-mapping patient-mapping careunit-mapping admissions-mapping diagnoses-mapping \
#         procedures-mapping omr-mapping microbiologyevents medications-mapping > run-tasks.out 2>&1 &
#
# Environment: IGNIFYR_URL (default http://localhost:6085/ignifyr), PROJECT_ID (mimic), JOB_ID (mimic-hosp-csv-to-fhir-server)
set -uo pipefail
IGNIFYR_URL="${IGNIFYR_URL:-http://localhost:6085/ignifyr}"
PROJECT_ID="${PROJECT_ID:-mimic}"
JOB_ID="${JOB_ID:-mimic-hosp-csv-to-fhir-server}"
API="${IGNIFYR_URL}/projects/${PROJECT_ID}/jobs/${JOB_ID}"
LOG="${RUN_TASKS_LOG:-run-tasks.log}"

SKIP_WRITE=false; TASKS=()
for arg in "$@"; do case "$arg" in --skip-write) SKIP_WRITE=true ;; *) TASKS+=("$arg") ;; esac; done
[ ${#TASKS[@]} -eq 0 ] && { echo "usage: $0 [--skip-write] <task-name> [<task-name> ...]"; exit 1; }
log() { echo "$(date +%FT%T) $*" | tee -a "$LOG"; }

running() { curl -sS -m 15 "$API/executions" 2>/dev/null | grep -q '"runningStatus":true'; }

for t in "${TASKS[@]}"; do
  while running; do log "another execution is still running - waiting"; sleep 60; done
  log "START $t (skipWrite=$SKIP_WRITE)"
  START=$(date +%s)
  R=$(curl -sS -m 1800 -w " http=%{http_code}" -X POST "$API/run" -H 'Content-Type: application/json' \
       -d "{\"clearCheckpoints\": false, \"skipWrite\": ${SKIP_WRITE}, \"mappingTaskNames\": [\"$t\"]}" 2>&1 | tail -c 200)
  log "POST -> $R"
  sleep 30
  # wait until the execution has started and finished again (the POST may return before or after the start)
  seen=0; idle=0
  while true; do
    if running; then seen=1; idle=0; else idle=$((idle+1)); fi
    if [ $seen -eq 1 ] && [ $idle -ge 2 ]; then break; fi
    if [ $seen -eq 0 ] && [ $idle -ge 10 ]; then log "WARN $t: no execution seen within 5 minutes after the POST"; break; fi
    sleep 30
  done
  log "END $t after $(( ($(date +%s) - START) / 60 )) min"
done
log "all tasks done"
