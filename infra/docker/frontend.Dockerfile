# syntax=docker/dockerfile:1.7
FROM nginx:1.31-alpine
COPY infra/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --chmod=755 infra/frontend/40-moby-config.sh /docker-entrypoint.d/40-moby-config.sh
COPY frontend/index.html frontend/config.js /usr/share/nginx/html/
COPY frontend/css /usr/share/nginx/html/css
COPY frontend/js /usr/share/nginx/html/js
COPY frontend/spec /usr/share/nginx/html/spec
COPY frontend/assets /usr/share/nginx/html/assets
HEALTHCHECK --interval=10s --timeout=3s --retries=10 CMD wget -qO- http://127.0.0.1/ > /dev/null || exit 1
