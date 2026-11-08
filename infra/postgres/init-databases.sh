#!/bin/sh
# Отдельная БД и отдельный пользователь на каждый сервис: сервис не видит чужие таблицы.
set -e
for svc in payment family rewards notification; do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<SQL
CREATE USER $svc WITH PASSWORD '$svc';
CREATE DATABASE $svc OWNER $svc;
REVOKE ALL ON DATABASE $svc FROM PUBLIC;
SQL
done
