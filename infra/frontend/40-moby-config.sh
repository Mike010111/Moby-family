#!/bin/sh
# Пишет window.MOBY_CONFIG (frontend/config.js) из переменных окружения контейнера.
# Запускается штатным entrypoint образа nginx из /docker-entrypoint.d перед стартом сервера.
set -eu
cat > /usr/share/nginx/html/config.js <<JS
window.MOBY_CONFIG = {
  apiBaseUrl: '${MOBY_API_BASE_URL:-.}',
  keycloakUrl: '${MOBY_KEYCLOAK_URL:-.}',
};
JS
