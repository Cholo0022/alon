#!/usr/bin/env bash
set -euo pipefail
umask 077

project=/opt/alon-prueba
run_id="$(date -u +%Y%m%d)_$(openssl rand -hex 4)"
database="alon_perf_validation_${run_id}"
network="alon-perf-${run_id}"
mysql_container="${network}-mysql"
report_dir="/root/alon-capacidad/${run_id}"
test_source="$project/backend/src/test/java/ar/com/alon/performance/CapacityBenchmarkTest.java"

if [[ ! -f "$test_source" ]]; then
  printf 'No se encuentra la prueba de capacidad en %s\n' "$test_source" >&2
  exit 1
fi

mkdir -p "$report_dir"
docker network create "$network" >/dev/null
cleanup() {
  docker rm -fv "$mysql_container" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
}
trap cleanup EXIT

export MYSQL_ROOT_PASSWORD="$(openssl rand -hex 32)"
export MYSQL_PASSWORD="$(openssl rand -hex 32)"
mysql_args=(
  -d --name "$mysql_container" --network "$network" --network-alias mysql
  --env MYSQL_ROOT_PASSWORD --env MYSQL_PASSWORD --env "MYSQL_DATABASE=$database"
  --env MYSQL_USER=benchmark
)
docker run "${mysql_args[@]}" mysql:8.4 >/dev/null

mysql_probe='MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h 127.0.0.1 -u benchmark "$MYSQL_DATABASE" -e "SELECT 1"'
for attempt in {1..60}; do
  if docker exec "$mysql_container" sh -c "$mysql_probe" >/dev/null 2>&1; then
    break
  fi
  if [[ "$attempt" == 60 ]]; then
    printf 'MySQL de prueba no quedó listo.\n' >&2
    exit 1
  fi
  sleep 2
done

export ALON_PERF_MYSQL_PASSWORD="$MYSQL_PASSWORD"
export ALON_PERF_MYSQL_URL="jdbc:mysql://mysql:3306/$database?sslMode=DISABLED&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&connectTimeout=5000&socketTimeout=130000"
printf 'Prueba aislada en %s. Resultados en %s\n' "$database" "$report_dir"
benchmark_args=(
  --rm --network "$network" --workdir /source
  --mount "type=bind,src=$project/backend,dst=/source,readonly"
  --mount "type=bind,src=$report_dir,dst=/results"
  --env ALON_PERF_MYSQL_URL --env ALON_PERF_MYSQL_PASSWORD
  --env ALON_PERF_MYSQL_USER=benchmark --env ALON_PERF_REPORT=/results/results.json
)
maven_args=(-B test -Dtest=CapacityBenchmarkTest -Dalon.build.directory=/tmp/alon-target)
docker run "${benchmark_args[@]}" maven:3.9-eclipse-temurin-21 mvn "${maven_args[@]}" 2>&1 | tee "$report_dir/execution.log"

test -s "$report_dir/results.json"
cat "$report_dir/results.json"
