#!/bin/sh
# Runs setup.sql (twice: it must be re-runnable) and the policy assertions in a throwaway Postgres.
# The tables and functions Supabase provides are stubbed in supabase_stub.sql.
set -eu
cd "$(dirname "$0")"
docker rm -f tibeb-pg-test >/dev/null 2>&1 || true
docker run -d --name tibeb-pg-test -e POSTGRES_PASSWORD=test postgres:17-alpine >/dev/null
trap 'docker rm -f tibeb-pg-test >/dev/null' EXIT
until docker exec tibeb-pg-test pg_isready -U postgres -q; do sleep 1; done
sleep 2
psql() { docker exec -i tibeb-pg-test psql -q -U postgres -v ON_ERROR_STOP=1 "$@"; }
psql < supabase_stub.sql
psql < ../setup.sql 2>/dev/null
psql < ../setup.sql 2>/dev/null
psql -t < policies_test.sql
