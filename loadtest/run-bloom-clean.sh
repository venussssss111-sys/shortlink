#!/usr/bin/env bash
set -u
cd /d/dafeiyuwork/shortlink/loadtest || exit 1
REDIS=shortlink-redis
MYSQL=shortlink-mysql
cmd_count() { docker exec $REDIS redis-cli info stats 2>/dev/null | grep '^total_commands_processed:' | cut -d: -f2 | tr -d '\r'; }
sel()       { docker exec $MYSQL mysql -uroot -p123456 -N -e "SHOW GLOBAL STATUS LIKE 'Com_select'" 2>/dev/null | awk '{print $2}'; }

JAVA=/g/Android/jdk/jdk-17.0.2/bin/java
JM=/g/develop/apache-jmeter-5.6.3/bin/ApacheJMeter.jar

echo "TIME_WAIT 现在 : $(netstat -an | grep -c TIME_WAIT)"
echo "等待 150 秒让压测机把端口收回来..."
sleep 150
echo "TIME_WAIT 冷却后: $(netstat -an | grep -c TIME_WAIT)"

run() {  # $1=tag  $2=csv
  rm -rf "report-$1"
  local a b s1 s2
  a=$(cmd_count); s1=$(sel)
  $JAVA -jar $JM -n -t redirect.jmx -Jport=8080 -Jthreads=100 -Jramp=10 \
      -Jduration=60 -Jcsv="$2" -l "result-$1.jtl" -j "jm-$1.log" -e -o "report-$1" > /dev/null 2>&1
  b=$(cmd_count); s2=$(sel)
  echo
  echo "########## [$1] ##########"
  echo "Redis 命令增量 = $((b-a))"
  echo "MySQL SELECT 增量 = $((s2-s1))"
  echo "TIME_WAIT 跑完 : $(netstat -an | grep -c TIME_WAIT)"
  python percentile.py "result-$1.jtl"
}

echo
echo "== B（干净环境，先跑）：不存在的码 / 布隆拦截 =="
run miss2 codes-invalid.csv

echo
echo "== 冷却 120 秒 =="
sleep 120

echo "== A（干净环境）：真实码 / 缓存命中 =="
run hit2 codes.csv
