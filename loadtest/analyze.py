import sys, csv
from statistics import mean

def pct(xs, p):
    if not xs: return 0
    k = (len(xs) - 1) * p / 100
    lo, hi = int(k), min(int(k) + 1, len(xs) - 1)
    return xs[lo] + (xs[hi] - xs[lo]) * (k - lo)

for path in sys.argv[1:]:
    lat, codes, ts = [], {}, []
    with open(path, newline='', encoding='utf-8', errors='replace') as f:
        r = csv.reader(f)
        header = next(r)
        idx_ts, idx_el, idx_rc = 0, 1, 3
        for row in r:
            if len(row) < 4: continue
            try:
                e = int(float(row[idx_el])); t = int(row[idx_ts])
            except ValueError:
                continue
            lat.append(e); ts.append(t)
            codes[row[idx_rc]] = codes.get(row[idx_rc], 0) + 1
    lat.sort()
    span = (max(ts) - min(ts)) / 1000.0
    n = len(lat)
    print(f"\n===== {path} =====")
    print(f"请求数 {n:,}   实际时长 {span:.1f}s   吞吐 {n/span:,.0f} QPS")
    print(f"平均 {mean(lat):.1f}ms  p50 {pct(lat,50):.0f}  p90 {pct(lat,90):.0f}  "
          f"p95 {pct(lat,95):.0f}  p99 {pct(lat,99):.0f}  最大 {max(lat)}")
    print("响应码:", ", ".join(f"{k}={v:,}" for k, v in sorted(codes.items(), key=lambda x: -x[1])))
    # 每 10 秒吞吐
    base = min(ts)
    buckets = {}
    for t in ts:
        b = (t - base) // 10000
        buckets[b] = buckets.get(b, 0) + 1
    line = "  ".join(f"{b*10:>2}~{b*10+10:>2}s:{c/10:,.0f}/s" for b, c in sorted(buckets.items()))
    print("每10秒吞吐:", line)
