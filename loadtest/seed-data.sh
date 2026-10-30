#!/usr/bin/env bash
# 生成 200 条压测用短链：自定义码 lt0001 ~ lt0200
# 用法：bash seed-data.sh   （应用必须已经在 8080 跑着）
set -u

BASE="${BASE:-http://localhost:8080}"
DIR="$(cd "$(dirname "$0")" && pwd)"
CSV="$DIR/codes.csv"
: > "$CSV"

ok=0
exists=0
fail=0

for i in $(seq 1 200); do
  code=$(printf 'lt%04d' "$i")
  http=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/links" \
    -H 'Content-Type: application/json' \
    -d "{\"originUrl\":\"https://example.com/lt/$i\",\"customCode\":\"$code\"}")

  if [ "$http" = "200" ]; then
    ok=$((ok + 1))
    echo "$code" >> "$CSV"
  elif [ "$http" = "409" ]; then
    # 已经存在，直接拿来用
    exists=$((exists + 1))
    echo "$code" >> "$CSV"
  else
    fail=$((fail + 1))
    echo "  [失败] $code -> HTTP $http"
  fi
done

echo "新建 $ok 条，已存在 $exists 条，失败 $fail 条"
echo "codes.csv 共 $(wc -l < "$CSV") 行"
