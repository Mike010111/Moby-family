# Прогресс

## Сделано
- **Этап 1.** Gradle multi-module (Kotlin DSL, version catalog, convention-плагины в `build-logic`), фронт перенесён
  в `/frontend`, `infra/docker-compose.yml` с Postgres (отдельная БД и пользователь на сервис), CI, ADR-001.
- **Этап 2. payment.** Money, счета, ledger двойной записи со сверкой, категории/MCC и хард-блоки, лимиты с
  календарными периодами и иерархией, `checkPayment`, QR-оплата с кэшбэком, P2P с котировкой
  (SEND/APPROVED/REQUEST/BLOCKED) и разовыми одобрениями, заморозка, тумблеры, подписки, автоперевод (ShedLock),
  копилочные SAVINGS-счета, идемпотентность, ProblemDetail, JWT-доступ. Тесты: периоды, лимиты, сценарии
  store.test.js, 403, идемпотентность, 50 параллельных оплат, автоперевод, Modulith verify.

## Дальше
- Этап 3: family.

## Известные проблемы
- нет
