#!/usr/bin/env bash
# Run the Locust scenarios (1, 2, 5, 10 users by default) against a running backend and record
# CPU/RSS of the backend JVM during each run.
#
#   scripts/run_loadtest.sh <label> [duration] [users...]
#   scripts/run_loadtest.sh sqlite 60s 1 2 5 10
#
# Results go to loadtest-results/<label>/: Locust CSV + HTML report per run, and resources-<n>.csv.
# Needs `locust` on PATH (pip install locust) and the backend listening on :8080.
set -euo pipefail
cd "$(dirname "$0")/.."

LABEL=${1:?usage: run_loadtest.sh <label> [duration] [users...]}
DURATION=${2:-60s}
shift $(( $# >= 2 ? 2 : $# )) || true
USERS=("$@"); [ ${#USERS[@]} -eq 0 ] && USERS=(1 2 5 10)
HOST=${HOST:-http://localhost:8080}
OUT="loadtest-results/$LABEL"
mkdir -p "$OUT"

PID=$(lsof -tiTCP:8080 -sTCP:LISTEN | head -1)
[ -n "$PID" ] || { echo "nothing listening on :8080"; exit 1; }
echo "backend pid $PID, label $LABEL, duration $DURATION, users: ${USERS[*]}"

SECS=${DURATION%s}   # duration is given as <n>s
MIN_SAMPLES=$(( SECS * 4 / 10 ))   # sampler ticks every ~2 s, so a healthy run has ~SECS/2

run_once() {  # $1 = users; returns 0 only if the run lasted as long as it should
  local N=$1 START END SAMPLER
  echo "t_s,cpu_pct,rss_mb" > "$OUT/resources-$N.csv"
  ( T=0; while true; do
      ps -o %cpu=,rss= -p "$PID" 2>/dev/null | awk -v t=$T '{printf "%d,%s,%.0f\n", t, $1, $2/1024}' >> "$OUT/resources-$N.csv" || break
      sleep 2; T=$((T+2))
    done ) &
  SAMPLER=$!
  START=$(date +%s)
  locust -f locustfile.py --host="$HOST" --headless -u "$N" -r "$N" -t "$DURATION" \
         --csv "$OUT/users-$N" --html "$OUT/report-$N.html" --only-summary 2>&1 | tail -25 || true  # locust exits 1 when any request failed; keep going
  END=$(date +%s)
  kill $SAMPLER 2>/dev/null || true; wait $SAMPLER 2>/dev/null || true
  # A laptop that sleeps mid-run makes locust's wall-clock timer expire early (few samples) or the run
  # overshoot: such a run says nothing about the app, so it is repeated.
  local SAMPLES=$(( $(wc -l < "$OUT/resources-$N.csv") - 1 ))
  if [ "$SAMPLES" -lt "$MIN_SAMPLES" ] || [ $(( END - START )) -gt $(( SECS + 20 )) ]; then
    echo "!! run invalid (samples=$SAMPLES, wall=$(( END - START ))s): machine probably slept"
    return 1
  fi
}

for N in "${USERS[@]}"; do
  echo "== $N user(s)"
  for ATTEMPT in 1 2 3 4 5; do
    run_once "$N" && break
    echo "!! retrying $N user(s), attempt $((ATTEMPT + 1))"
    sleep 5
  done
  sleep 3
done
echo "done -> $OUT"
