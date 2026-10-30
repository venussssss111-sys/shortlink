#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""从 JMeter 的 .jtl 结果文件里算分位数。
用法：python percentile.py result.jtl
"""
import csv
import sys
from collections import Counter

path = sys.argv[1] if len(sys.argv) > 1 else "result.jtl"

elapsed = []
codes = Counter()
with open(path, newline="", encoding="utf-8") as f:
    for row in csv.DictReader(f):
        try:
            elapsed.append(int(row["elapsed"]))
        except (KeyError, ValueError):
            continue
        codes[row.get("responseCode", "?")] += 1

if not elapsed:
    print("没读到样本，检查文件路径和文件是否为空")
    sys.exit(1)

elapsed.sort()
n = len(elapsed)


def pct(p):
    idx = min(n - 1, max(0, int(round(n * p / 100.0)) - 1))
    return elapsed[idx]


print("样本数   : %d" % n)
print("响应码   : %s" % dict(codes))
print("平均     : %.1f ms" % (sum(elapsed) / n))
print("最快     : %d ms" % elapsed[0])
print("p50      : %d ms" % pct(50))
print("p90      : %d ms" % pct(90))
print("p95      : %d ms" % pct(95))
print("p99      : %d ms" % pct(99))
print("最慢     : %d ms" % elapsed[-1])
