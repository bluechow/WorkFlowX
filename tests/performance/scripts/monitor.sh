#!/bin/bash
# 轻量资源采样（每 2s 一次，直到 stop 文件出现）
OUT="$1"
mkdir -p "$OUT"
(/c/Windows/System32/wsl.exe -d Ubuntu -u root -- sh -c "while [ ! -f /tmp/perf_stop ]; do docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}}' \$(docker ps -q | tr '\n' ' ') >> /tmp/perf_docker.csv 2>/dev/null; echo \"\$(date +%s),\$(top -bn1 | head -4 | tail -1)\" >> /tmp/perf_host.log 2>/dev/null; sleep 2; done" &) 
powershell -Command "Start-Process -FilePath 'wsl.exe' -ArgumentList '-d','Ubuntu','-u','root','--','sh','-c','while [ ! -f /tmp/perf_stop ]; do docker stats --no-stream --format \"{{.Name}},{{.CPUPerc}},{{.MemUsage}}\" >> /tmp/perf_docker.csv 2>/dev/null; sleep 2; done' -WindowStyle Hidden"
echo "monitor started -> $OUT"
