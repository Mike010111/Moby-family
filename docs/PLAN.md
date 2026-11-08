# Moby Family backend — план

Источник истины по поведению — JS-прототип (`frontend/js/core/rules.js`, `frontend/spec/actions.js`,
`frontend/js/core/artifacts.js`, `frontend/spec/initial-state.js`, `frontend/spec/store.test.js`).
Где бэкенд сознательно отличается — см. `docs/DECISIONS.md`.

## 1. Модули репозитория

| Модуль | Назначение | Порт | БД |
|---|---|---|---|
| `libs/common` | контракты событий Kafka, `ErrorCode`, `Money`, `BusinessException` | — | — |
| `services/gateway` | Spring Cloud Gateway (WebFlux): маршрутизация `/api/**`, проверка JWT | 8080 | — |
| `services/payment` | счета, ledger, QR-оплата, P2P, лимиты, категории, заморозка, подписки, автоперевод | 8081 | `payment` |
| `services/family` | семья, родственники, дети, запросы, задания, копилки, уроки, гео | 8082 | `family` |
| `services/rewards` | баллы, магазин артефактов, кэшбэк-предложения, продление | 8083 | `rewards` |
| `services/notification` | inbox ребёнка, лента родителя, SSE | 8084 | `notification` |
| `frontend` | прототип; слой данных заменён на API-клиент | 5173 (nginx) | — |
| `infra` | docker-compose, realm Keycloak, Prometheus, Grafana, OTel | | |

Пакеты: `by.moby.<service>.<module>`. Внутри сервиса — модули Spring Modulith
(проверяются `ApplicationModules.verify()`), слои `api` (REST + DTO + MapStruct) → `application`
(транзакционные сценарии) → `domain` (сущности, правила, машины состояний) → `infrastructure`.

## 2. Идентичность и права

JWT от Keycloak (realm `moby`) содержит: `member_id` (`mom`, `misha`, `ivan`), `family_id` (`fam-demo`),
`roles` (`PARENT` | `CHILD_JUNIOR` | `CHILD_TEEN`). Каждый сервис хранит `family_id` у своих записей и
проверяет доступ локально, без сетевых вызовов:

* `PARENT` — только дети своей семьи (`resource.familyId == jwt.family_id`);
* `CHILD_*` — только свои ресурсы (`childId == jwt.member_id`);
* операции «только родитель» (одобрение, лимиты, заморозка, тумблеры, задания, автоперевод) — 403 для детей.

Межсервисные вызовы (family → payment, payment → rewards) идут через `RestClient` с таймаутами и
**ретранслируют JWT пользователя** — права проверяются тем же кодом, что и для внешних вызовов.

## 3. Доменная модель и схемы БД

### payment
* `accounts` (id, family_id, owner_id, kind `PARENT|CHILD|SAVINGS|SYSTEM`, cohort, holder_* (подписи
  для текстов), balance numeric(19,2), frozen, card_* (маска, номер, срок…), version)
* `ledger_transactions` (id, kind, created_at, idempotency_key) и `ledger_entries` (transaction_id,
  account_id, amount signed numeric(19,2)) — сумма проводок транзакции = 0; баланс счёта = Σ проводок.
* `account_operations` — строки выписки ребёнка (title, amount, mcc, category, online, status
  `COMPLETED|DECLINED`, decline_code, note, created_at, ledger_transaction_id). Индекс
  `(account_id, created_at desc)` — выписка и подсчёт трат за период.
* `category_permissions` (account_id, category_key, allowed)
* `spending_limits` (id, account_id, count, unit, amount, categories text[], start_at)
* `p2p_approvals` (id, account_id, request_id, max_amount, status `ACTIVE|REPLACED|USED`, used_amount)
* `subscriptions` (id, account_id, title, price, next_label, active, icon)
* `autopay_rules` (account_id PK, enabled, amount, every, weekday, month_day, last_run_on)
* `idempotency_records` (owner_id, key, operation, request_hash, status, body jsonb) UNIQUE(owner_id, key)
* `event_publication` (outbox Spring Modulith), `shedlock`

### family
* `families`, `members` (id, family_id, kind `RELATIVE|CHILD`, name, label, падежи, main, female,
  age, cohort, surname, name_gen, geo_*, battery, frozen — проекция из payment)
* `requests` (id, family_id, child_id, relative_id, kind `TOPUP|P2P_OVER|PIGGY_UPDATE|PIGGY_DELETE`,
  amount, reason, contact_name, piggy_id, status `SENT|PENDING|APPROVED|DECLINED|CANCELLED`,
  resolution, created_at, resolved_at). Частичный уникальный индекс: одна `PENDING` на копилку.
* `tasks` (id, child_id, title, reward, status `OPEN|REVIEW|DONE`, created_at)
* `piggies` (id, child_id, goal, target, saved, status `ACTIVE|COMPLETED|CLOSED`) — деньги копилки
  лежат на SAVINGS-счёте в payment, `saved` — подтверждённое зеркало из ответов payment.
* `lesson_progress` (child_id, done)
* `processed_events`, `event_publication`

### rewards
* `point_accounts` (member_id, family_id, cohort, points), `point_entries` (журнал начислений/списаний,
  source_event_id уникален)
* `artifact_states` (child_id, cohort, week_slot, week_start), `owned_artifacts` (child_id, slot,
  artifact_id, cashback, cycles, bought_at, active_until), `sold_out` (child_id, artifact_id, week_start)
* каталог артефактов/редкостей — код (как в `artifacts.js`)
* `processed_events`, `event_publication`

### notification
* `notifications` (id, family_id, audience `PARENT_FEED|CHILD_INBOX`, member_id, kind, tone, title,
  text, request_id, task_id, created_at, seen). Индекс `(family_id, audience, created_at desc)`.
* `processed_events`

## 4. REST (через gateway, префикс `/api/<service>`)

**payment** — `GET /me/parent-account`, `GET /children/{id}/account` (баланс, карта, категории,
лимиты с usage/periodTitle, автоперевод, подписки, одобрения), `GET /children/{id}/operations`,
`PUT /children/{id}/frozen`, `PUT /children/{id}/categories/{key}`, `POST|PUT|DELETE /children/{id}/limits`,
`POST /children/{id}/topups`*, `POST /children/{id}/qr-payments`*, `GET /children/{id}/p2p/quote`,
`POST /children/{id}/p2p-transfers`*, `PUT /children/{id}/autopay`, `PUT|DELETE /children/{id}/subscriptions/{sid}`,
`GET /merchants`, `GET /contacts`, `GET /catalog/categories`.
Внутренние (для family, JWT пользователя): `POST /internal/transfers/parent-to-child`*,
`POST /internal/savings/{piggyId}/deposit|withdraw`*, `POST /internal/savings`, `POST /internal/children/{id}/p2p-approvals`.
`*` — обязателен `Idempotency-Key`.

**family** — `GET /me` (семья, родственники, дети, задания, копилки, уроки, гео), `GET /requests`,
`POST /requests` (topup), `POST /requests/p2p-over`, `POST /requests/{id}/approve|decline`,
`POST /children/{id}/tasks`, `POST /tasks/{id}/submit|approve`, `POST /children/{id}/lessons/complete`,
`POST /children/{id}/piggies`, `POST /piggies/{id}/deposit`*, `PUT /piggies/{id}/target`, `DELETE /piggies/{id}`,
`POST /children/{id}/geo/refresh`.

**rewards** — `GET /me/points`, `GET /children/{id}` (баллы, артефакты, предложения магазина, продление),
`POST /children/{id}/artifacts/{artifactId}/purchase`, `POST /children/{id}/artifacts/renew`,
`GET /children/{id}/payment-perks?category=&amount=` (для payment).

**notification** — `GET /feed`, `GET /inbox`, `POST /inbox/seen`, `GET /stream` (SSE).

**demo** (профиль `demo`) — `POST /api/<service>/demo/reset` в каждом сервисе.

Ошибки — `application/problem+json` (RFC 9457) с расширением `code` (см. `ErrorCode`).

## 5. События (topic → producer → consumers)

| Событие | Topic | Producer | Consumers |
|---|---|---|---|
| PaymentCompleted | `payment.events` | payment | rewards (баллы), notification (лента) |
| PaymentDeclined | `payment.events` | payment | notification |
| TransferCompleted | `payment.events` | payment | notification (метрики/журнал) |
| CashbackGranted | `payment.events` | payment | rewards (статистика артефакта) |
| AccountFrozen / AccountUnfrozen | `payment.events` | payment | family (проекция frozen), notification |
| RequestCreated / RequestResolved | `family.events` | family | notification |
| TaskSubmitted / TaskApproved | `family.events` | family | notification, rewards (+5) |
| PiggyDeposited | `family.events` | family | rewards (+2) |
| PiggyCompleted | `family.events` | family | notification |
| LessonCompleted | `family.events` | family | rewards (+10) |
| ArtifactPurchased | `rewards.events` | rewards | notification |

Публикация — только через transactional outbox (Spring Modulith Event Publication Registry, JPA →
Kafka externalization). Ключ сообщения — `childId` (порядок событий по ребёнку). Консьюмеры
идемпотентны: `processed_events(event_id, consumer)` в той же транзакции, что и обработка.

## 6. Распределение правил из JS

| JS | Сервис / класс |
|---|---|
| `CATEGORIES`, `HARD_BLOCKS`, `groupOf`, `categoriesFor` | payment `catalog.Categories` |
| `parseMoney`, `round2` | common `Money` |
| `unitStart`, `limitPeriod`, `periodTitle`, `periodLabel` | payment `limits.LimitPeriods` |
| `txMatchesLimit`, `limitUsage`, `tightestLimit` | payment `limits.LimitUsageCalculator` |
| `validateLimit` (COUNT/UNIT/AMOUNT/CATEGORY/DUPLICATE/HIERARCHY) | payment `limits.LimitValidator` |
| `checkPayment` | payment `payments.PaymentPolicy` |
| `quoteP2P`, `activeApproval` | payment `p2p.P2pQuoteService` |
| `QR_MERCHANTS`, `CONTACTS` | payment `catalog` |
| `transferFromParent`, `parent/topUp`, `parent/toggleFreeze`, `parent/toggleAllow` | payment |
| `autopay/update` (+ исполнение по расписанию, ShedLock) | payment `autopay` |
| `sub/*` | payment `subscriptions` |
| `request/*`, `p2p/requestOver` | family `requests` (машина состояний) |
| `task/*`, `parent/addTask` | family `tasks` (машина состояний) |
| `piggy/*`, `completePiggy`, `removePiggy` | family `piggies` (+ payment SAVINGS) |
| `lesson/complete`, `parent/refreshGeo` | family |
| `ARTIFACTS`, `RARITY`, `SHOP`, `offerState`, `renewOffer`, `cashbackFor`, `artifact/*` | rewards |
| `cashbackAmount` | payment (начисление), процент — из rewards |
| баллы: QR (+max(1, round(amount))), копилка (+2), задание (+5), урок (+10) | rewards (по событиям) |
| `notify`, `pushFeed` (тексты) | notification `NotificationTexts` |

## 7. Этапы

1. Каркас, перенос фронта, compose (Postgres), CI, ADR-001.
2. payment целиком + тест конкурентности.
3. family.
4. outbox + Kafka в payment/family; rewards; notification (SSE); Testcontainers-интеграция; ADR outbox.
5. Keycloak, Spring Security во всех сервисах и gateway, тесты прав.
6. Фронтенд на API-клиенте.
7. Наблюдаемость, JaCoCo, ArchUnit, README, ADR.
