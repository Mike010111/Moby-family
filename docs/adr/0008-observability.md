# ADR-008. Наблюдаемость

* Статус: принято
* Дата: 2026-11-08

## Решение

* **Метрики** — Micrometer → `/actuator/prometheus`, Prometheus опрашивает все сервисы раз в 10 с. Гистограммы
  `http.server.requests` для p95. Бизнес-метрики: `moby_payments_completed_total`,
  `moby_payments_declined_total{code}` (причины `checkPayment`), `moby_outbox_pending` (недоставленные события),
  `moby_rewards_perks_failures_total`, `moby_sse_subscribers`. OTLP-экспорт метрик выключен — модель pull.
* **Grafana** (provisioning из `infra/grafana`): дашборд «Moby Family — сервисы и платежи» — оплаты, отказы по
  причинам, outbox, SSE, RPS, p95, 4xx/5xx, Kafka, JVM. Открыт анонимно на чтение, админ — `admin/admin`.
* **Трейсы** — OpenTelemetry (`spring-boot-starter-opentelemetry`, мост Micrometer Tracing) → OTLP HTTP →
  **Jaeger v2** (all-in-one, in-memory). Сэмплинг 100 % (стенд). Контекст W3C проходит через gateway, RestClient
  (family → payment, payment → rewards) и Kafka (observation у KafkaTemplate и listener).
  traceId/spanId — в каждой строке лога.
* **Health** — `/actuator/health/{liveness,readiness}`; readiness — healthcheck контейнеров в compose.

## Почему Jaeger

Для трейсов нужен бэкенд; Jaeger v2 принимает OTLP напрямую, не требует отдельного коллектора и хранилища и
даёт UI. Альтернативы — Grafana Tempo (+ хранилище/конфиг) или OTel Collector перед Jaeger — оправданы, когда
нужны маршрутизация и обогащение телеметрии; здесь лишние.

## Ограничения

* Отправка события из outbox (после коммита, в фоне) начинает новый трейс: контекст запроса не сохраняется в
  `event_publication`. Цепочка «HTTP-запрос → событие → консьюмеры» связывается по `eventId`/ключу ребёнка.
* Jaeger хранит трейсы в памяти — после перезапуска контейнера они пропадают.
* Prometheus, Grafana и Jaeger — compose-профиль `observability` (ADR-010). Экспорт трейсов включается вместе с
  ним (`TRACING_EXPORT_ENABLED=true`): без Jaeger экспортёр OTLP писал бы в лог ошибку на каждый пакет спанов.
  Метрики (`/actuator/prometheus`) и traceId в логах работают всегда.
