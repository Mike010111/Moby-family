# Прогресс

## Сделано
- **Этап 1.** Gradle multi-module (Kotlin DSL, version catalog, convention-плагины в `build-logic`), фронт перенесён
  в `/frontend`, `infra/docker-compose.yml` с Postgres (отдельная БД и пользователь на сервис), CI, ADR-001.
- **Этап 2. payment.** Money, счета, ledger двойной записи со сверкой, категории/MCC и хард-блоки, лимиты с
  календарными периодами и иерархией, `checkPayment`, QR-оплата с кэшбэком, P2P с котировкой
  (SEND/APPROVED/REQUEST/BLOCKED) и разовыми одобрениями, заморозка, тумблеры, подписки, автоперевод (ShedLock),
  копилочные SAVINGS-счета, идемпотентность, ProblemDetail, JWT-доступ. Тесты: периоды, лимиты, сценарии
  store.test.js, 403, идемпотентность, 50 параллельных оплат, автоперевод, Modulith verify.

- **Этап 3. family.** Родственники и дети (когорты), запросы TOPUP/P2P_OVER/PIGGY_UPDATE/PIGGY_DELETE с машиной
  состояний и одобрением, задания (OPEN→REVIEW→DONE), копилки (7–13 через одобрение, 14–17 сразу), уроки, GPS,
  проекция заморозки. Деньги — только через `PaymentClient` (RestClient, таймауты, ретрансляция JWT, перевод
  ProblemDetail). Тесты: сценарии store.test.js (через in-memory fake payment), 403, контракт клиента, Modulith.

- **Этап 4. События.** Transactional outbox (Spring Modulith → Kafka) в payment, family, rewards с повторной
  отправкой (ShedLock); идемпотентные консьюмеры (`processed_events`). rewards: баллы по событиям, магазин
  артефактов (покупка/замена/SOLDOUT/EQUIPPED/POINTS, продление), перки для payment, статистика кэшбэка.
  notification: лента/inbox с текстами из actions.js, SSE (`notification`, `changed`). Testcontainers Kafka ITs.
  ADR-002/003/004.

- **Этап 5. Безопасность.** Keycloak realm (роли, мапперы `member_id`/`family_id`/`roles`, демо-пользователи
  anna/misha/ivan + olga из другой семьи), resource server во всех сервисах и в gateway (WebFlux), SSE-маршрут без
  таймаута. Тесты прав в каждом сервисе, `GatewayRoutingTest`. ADR-005.

## Дальше
- Этап 6: Docker-образы, фронтенд на API-клиенте, smoke.sh.

## Известные проблемы
- нет
