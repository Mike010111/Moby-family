#!/usr/bin/env bash
# Старт стенда в GitHub Codespaces (postStartCommand). Можно запустить и вручную: bash .devcontainer/start.sh
#  1. Публичные адреса портов вычисляются из переменных Codespaces и пишутся в .env (его читает compose):
#     фронтенд — origin для CORS и клиента Keycloak, Keycloak — его hostname и issuer токенов.
#  2. Образы — из GitHub Container Registry; каких нет (пакеты не опубликованы) — собираются из исходников.
#  3. docker compose up --wait — до healthy всех контейнеров.
# Prometheus, Grafana и Jaeger: COMPOSE_PROFILES=observability TRACING_EXPORT_ENABLED=true docker compose up -d --wait
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -n "${CODESPACE_NAME:-}" ]; then
  domain=${GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN:-app.github.dev}
  owner=$(printf %s "${GITHUB_REPOSITORY:-}" | cut -d/ -f1 | tr "[:upper:]" "[:lower:]")
  {
    echo "FRONTEND_PUBLIC_URL=https://${CODESPACE_NAME}-5173.${domain}"
    echo "KEYCLOAK_PUBLIC_URL=https://${CODESPACE_NAME}-8180.${domain}"
    if [ -n "$owner" ]; then echo "MOBY_IMAGES=ghcr.io/${owner}"; fi
  } > .env
  echo "Адреса стенда (.env):"
  sed 's/^/  /' .env
fi

# Docker-in-Docker поднимается вместе с контейнером — ждём демон
for _ in $(seq 1 60); do docker info > /dev/null 2>&1 && break; sleep 2; done

echo "Скачиваю образы из ghcr.io (недоступные будут собраны из исходников)…"
docker compose pull --ignore-pull-failures --quiet || true
docker compose up -d --wait --wait-timeout 900
echo "Стенд готов: фронтенд — порт 5173 (вкладка Ports), smoke-тест — scripts/smoke.sh"
