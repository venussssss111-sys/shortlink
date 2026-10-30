#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""把 loadtest 目录下所有 jtl 汇总成一张表（每行一个文件）。

用法：python summarize_all.py
"""
import csv
import glob
import os
from collections import Counter

HEADER = "| 文件 | 样本数 | 时长(s) | QPS | 平均(ms) | p50 | p90 | p95 | p99 | 最慢 | 响应码 |"
SEP = "|---|---|---|---|---|---|---|---|---|---|---|"

print(HEADER)
print(SEP)

for path in sorted(glob.glob("*.jtl")):
    elapsed = []
    codes = Counter()
    ts_min, ts_max = None, None
    n = 0
    with open(path, newline="", encoding="utf-8", errors="replace") as f:
        for row in csv.DictReader(f):
            try:
                e = int(row["elapsed"])
                ts = int(row["timeStamp"])
            except (KeyError, ValueError):
                continue
            elapsed.append(e)
            n += 1
            ts_min = ts if ts_min is None or ts < ts_min else ts_min
            ts_max = ts if ts_max is None or ts > ts_max else ts_max
            c = row.get("responseCode", "?")
            if c != "302" and c != "404":
                c = c[:40]
            codes[c] += 1

    if not elapsed:
        print("| %s | 空 | | | | | | | | | |" % path)
        continue

    elapsed.sort()
    span = max(0.001, (ts_max - ts_min) / 1000.0)
    qps = n / span
    avg = sum(elapsed) / n

    def pct(p):
        idx = min(n - 1, max(0, int(round(n * p / 100.0)) - 1))
        return elapsed[idx]

    top = ", ".join("%s×%d" % (k, v) for k, v in codes.most_common(3))
    print("| %s | %d | %.1f | %.0f | %.1f | %d | %d | %d | %d | %d | %s |"
          % (path, n, span, qps, avg, pct(50), pct(90), pct(95), pct(99), elapsed[-1], top))
