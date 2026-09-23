"""JTL 结果聚合分析（Phase 14; P14-24）。

用法: python analyze_jtl.py results/level0-baseline.jtl
输出: 每个 sampler 的请求数/错误率/Throughput/Avg/P50/P90/P95/P99/min/max。
"""
from __future__ import annotations

import csv
import statistics
import sys
from collections import defaultdict
from pathlib import Path


def percentile(values: list[int], p: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    idx = max(0, min(len(ordered) - 1, round(p / 100 * len(ordered) - 0.5)))
    return ordered[idx]


def analyze(jtl_path: str) -> None:
    rows: dict[str, list[tuple[int, bool]]] = defaultdict(list)
    start = end = None
    with open(jtl_path, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            label = row["label"]
            elapsed = int(row["elapsed"])
            ok = row["success"] == "true"
            rows[label].append((elapsed, ok))
            ts = int(row["timeStamp"])
            start = ts if start is None else min(start, ts)
            end = ts if end is None else max(end, ts)
    duration_s = max((end - start) / 1000, 0.001)

    print(f"JTL: {jtl_path}  持续 {duration_s:.1f}s")
    print(f"{'sampler':<24}{'reqs':>6}{'err%':>8}{'thr/s':>8}{'avg':>8}{'P50':>7}{'P90':>7}{'P95':>7}{'P99':>7}{'min':>6}{'max':>7}")
    total_req = total_err = 0
    for label, samples in sorted(rows.items()):
        times = [t for t, _ in samples]
        errs = sum(1 for _, ok in samples if not ok)
        total_req += len(samples)
        total_err += errs
        thr = len(samples) / duration_s
        print(f"{label:<24}{len(samples):>6}{errs / len(samples) * 100:>7.1f}%{thr:>8.1f}"
              f"{statistics.mean(times):>8.0f}{percentile(times, 50):>7.0f}{percentile(times, 90):>7.0f}"
              f"{percentile(times, 95):>7.0f}{percentile(times, 99):>7.0f}{min(times):>6}{max(times):>7}")
    err_rate = total_err / total_req * 100 if total_req else 0
    print(f"{'TOTAL':<24}{total_req:>6}{err_rate:>7.1f}%{total_req / duration_s:>8.1f}")


if __name__ == "__main__":
    if len(sys.argv) != 2 or not Path(sys.argv[1]).exists():
        print(__doc__)
        sys.exit(1)
    analyze(sys.argv[1])
