#!/bin/bash
# P14 性能场景运行器（防 JTL 追加污染：运行前删除同名结果文件）
# 用法: ./scripts/run-perf.sh <level> <threads> <rampup> <duration> <jmx>
set -e
LEVEL="$1"; THREADS="$2"; RAMPUP="$3"; DURATION="$4"; JMX="${5:-jmeter/workflowx-perf-jsr223.jmx}"
PROJECT_ID="${PERF_PROJECT_ID:?PERF_PROJECT_ID must be set (from prepare_data.py output)}"
cd "$(dirname "$0")/.."
JTL="results/${LEVEL}.jtl"
LOG="results/${LEVEL}.log"
rm -f "$JTL" "$LOG"
echo "===== ${LEVEL}: ${THREADS} threads / ramp ${RAMPUP}s / ${DURATION}s ====="
jmeter -n -t "$JMX" -q perf.properties \
  -JPERF_PROJECT_ID="$PROJECT_ID" -JTHREADS="$THREADS" -JRAMP_UP="$RAMPUP" -JDURATION="$DURATION" \
  -l "$JTL" -j "$LOG" 2>&1 | grep -vE "WARN|package scanning" | tail -1
python scripts/analyze_jtl.py "$JTL"
