#!/usr/bin/env bash
set -u
cd /d/dafeiyuwork/shortlink/loadtest || exit 1
REDIS=shortlink-redis
MYSQL=shortlink-mysql
cmd_count() { docker exec $REDIS redis-cli info stats 2>/dev/null | grep '^total_commands_processed:' | cut -d: -f2 | tr -d '\r'; }
sel()       { docker exec $MYSQL mysql -uroot -p123456 -N -e "SHOW GLOBAL STATUS LIKE 'Com_select'" 2>/dev/null | awk '{print $2}'; }

docker exec $MYSQL mysql -uroot -p123456 -N -e "SELECT code FROM shortlink.short_link WHERE status=1 ORDER BY id" 2>/dev/null > codes.csv
printf 'zz%04d\n' $(seq 1 200) > codes-invalid.csv
echo "codes.csv 行数=$(wc -l < codes.csv) 首行=$(head -1 codes.csv)"
echo "codes-invalid.csv 行数=$(wc -l < codes-invalid.csv)"

JAVA=/g/Android/jdk/jdk-17.0.2/bin/java
JM=/g/develop/apache-jmeter-5.6.3/bin/ApacheJMeter.jar

run() {  # $1=tag  $2=csv
  rm -rf "report-$1"
  local a b s1 s2
  a=$(cmd_count); s1=$(sel)
  $JAVA -jar $JM -n -t redirect.jmx -Jport=8080 -Jthreads=100 -Jramp=10 \
      -Jduration=60 -Jcsv="$2" -l "result-$1.jtl" -j "jm-$1.log" -e -o "report-$1" > /dev/null 2>&1
  b=$(cmd_count); s2=$(sel)
  echo
  echo "########## [$1] ##########"
  echo "Redis 命令增量 = $((b-a))   （60 秒内全部请求）"
  echo "MySQL SELECT 增量 = $((s2-s1))"
  python percentile.py "result-$1.jtl"
}

echo
echo "== A. 真实码（缓存命中）=="
run hit codes.csv

echo
echo "== 冷却 90 秒（等 TIME_WAIT 散去）=="
sleep 90

echo
echo "== B. 不存在的码（布隆拦截）=="
run miss codes-invalid.csv
