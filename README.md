# Moby Family

[![CI](https://github.com/Mike010111/Moby-family/actions/workflows/ci.yml/badge.svg)](https://github.com/Mike010111/Moby-family/actions/workflows/ci.yml)

Детско-семейный банкинг в Moby (МТБанк). Родитель видит счета детей, ставит лимиты и одобряет запросы.
Ребёнок 7–13 лет платит по QR и копит, подросток 14–17 переводит деньги и управляет подписками. Бэкенд — Java 21 /
Spring Boot 4, фронтенд — прототип хакатона без изменений в интерфейсе.

**[Открыть демо](https://mike010111.github.io/Moby-family/)** — интерфейс с логикой в браузере, без бэкенда.<br>
**[Запустить в Codespaces](https://codespaces.new/Mike010111/Moby-family?quickstart=1)** — весь стенд с бэкендом,
фронтенд откроется сам.

## Запуск

```bash
docker compose up -d --build --wait
```

Откройте http://localhost:5173. Вкладки сверху — вход под демо-пользователями (пароль `demo`): **anna** — мама,
**misha** — 9 лет, **ivan** — 17 лет. «Сбросить демо» возвращает исходные данные.

Swagger — http://localhost:8081/swagger-ui.html (payment; family, rewards, notification — порты 8082–8084),
Keycloak — http://localhost:8180 (admin / admin). Остановить стенд — `docker compose down`.

Метрики и трейсы (Prometheus :9090, Grafana :3000, Jaeger :16686):

```bash
COMPOSE_PROFILES=observability TRACING_EXPORT_ENABLED=true docker compose up -d --wait
```

## Устройство

| Сервис | Что делает |
|---|---|
| payment | счета, ledger, оплата по QR, P2P, лимиты, заморозка, подписки, автоперевод |
| family | семья, запросы с одобрением, задания, копилки, уроки |
| rewards | баллы, артефакты, кэшбэк |
| notification | лента родителя, уведомления ребёнку (SSE) |
| gateway | вход в API, проверка JWT |

У каждого сервиса своя база. События идут через Kafka, вход — через Keycloak.

* **Деньги** хранятся в `BigDecimal` и проходят через ledger двойной записи. Проверка лимита и списание выполняются
  в одной транзакции под `SELECT … FOR UPDATE`. → [ADR-007](docs/adr/0007-money-ledger-concurrency.md)
* **Повтор запроса с тем же `Idempotency-Key`** возвращает сохранённый ответ и не создаёт вторую операцию.
* **События** публикуются только через transactional outbox, обработчики идемпотентны. → [ADR-002](docs/adr/0002-transactional-outbox.md)
* **Права:** родитель видит только своих детей, ребёнок — только себя. → [ADR-005](docs/adr/0005-security-keycloak-jwt.md)

Остальные решения — в [docs/adr](docs/adr) и [docs/DECISIONS.md](docs/DECISIONS.md).

## Тесты

```bash
./mvnw clean verify               # JUnit + Testcontainers (Postgres, Kafka), ArchUnit, покрытие ≥ 80 %
cd frontend && npm test           # сценарии прототипа — спецификация поведения
cd frontend && npm run test:e2e   # Playwright: демо без бэкенда
```

Каждый сценарий прототипа проверен и на бэкенде, соответствие — в [frontend/spec](frontend/spec/README.md).
