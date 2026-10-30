#!/usr/bin/env bash
# 清掉压测数据（只删 lt 开头的短链）
set -u

docker exec shortlink-mysql mysql -uroot -p123456 shortlink \
  -e "DELETE FROM short_link WHERE code LIKE 'lt%'; SELECT COUNT(*) AS remain FROM short_link;"

echo "已清理。codes.csv 保留着，下次 seed 会重新生成。"
