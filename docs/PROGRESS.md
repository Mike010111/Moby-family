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

- **Этап 6. Фронтенд и стенд.** `ApiStore` вместо локального store (тот же интерфейс, состояние из API,
  SSE), экраны — только `await` у результатов; вкладки жюри = вход под anna/misha/ivan; «Сбросить демо» —
  reset во всех сервисах. Прототипные редьюсеры → `frontend/spec`. Dockerfile (общая стадия сборки),
  nginx, полный compose, `scripts/smoke.sh`. ADR-006.
- **Проверка фронтенда.** Скрипт гоняет `ApiStore` в Node против живого стенда через nginx и рендерит настоящие
  `parentView/kidView/teenView` из полученного состояния: 25 проверок (запрос → одобрение, HARD_BLOCK,
  артефакт → кэшбэк, P2P NEEDS_APPROVAL → запрос, заморозка, иерархия лимитов, подписка, копилка, SSE).

- **Этап 7. Наблюдаемость и качество.** Prometheus (все сервисы), Grafana-дашборд (оплаты, отказы по причинам,
  outbox, SSE, RPS, p95, 4xx/5xx, Kafka, JVM), OpenTelemetry → Jaeger (трейсы gateway → payment → rewards,
  gateway → family → payment, Kafka → консьюмеры), JaCoCo с порогом 80 % строк, ArchUnit (слои, DTO, Clock,
  без field injection, без double), README со схемой, ADR-007/008, CI-job e2e (compose + smoke).

## Финальная проверка (2026-11-08)

| Проверка | Результат |
|---|---|
| `./gradlew build` | зелёный: 119 JUnit-тестов, 0 падений, ArchUnit, Modulith verify, JaCoCo ≥ 80 % (строки: payment 89 %, family 89 %, rewards 86 %, notification 84 %, gateway 87 %) |
| `docker compose up -d` | 12 контейнеров healthy: postgres, kafka, keycloak, payment, family, rewards, notification, gateway, frontend, prometheus, grafana, jaeger |
| `scripts/smoke.sh` | все шаги пройдены: вход, права 200/403/401, «5 BYN на обед» → одобрение → балансы и уведомление, игровые автоматы → HARD_BLOCK, P2P Ивана 35 BYN → NEEDS_APPROVAL → запрос → одобрение → перевод, артефакт → кэшбэк 5 % при оплате по QR |
| `cd frontend && npm test` | 25 / 25 сценариев прототипа |
| Фронтенд против стенда | `ApiStore` + рендер `parentView/kidView/teenView`: 25 проверок пройдены |

## Соответствие сценариев `frontend/spec/store.test.js` и JUnit

| # | Сценарий прототипа | JUnit-аналог |
|---|---|---|
| 1 | Запрос «5 BYN на обед» главному родственнику → одобрение меняет оба баланса и уведомляет ребёнка | family `FamilyScenariosTest.lunchRequestApproved`; payment `TransferController` (внутренний перевод) в `IdempotencyTest.concurrentRetriesOfOneTopUp`; notification `NotificationTextsTest.resolutionTextsAgreeWithRelativeGender`, `NotificationFlowTest.requestFlow`; smoke шаг 1 |
| 2 | Запрос не главному родственнику уходит, но в хаб не попадает | family `FamilyScenariosTest.requestToOtherRelative`; notification `NotificationFlowTest.requestFlow` (toMain=false — нет в ленте) |
| 3 | Суммы — не больше двух знаков после запятой | common `MoneyTest.parsesLikeTheJsPrototype`; payment `PaymentScenariosTest.amountsHaveAtMostTwoDecimals` |
| 4 | Лимит меньше уже потраченного: ничего не ломается, оплата отклоняется | payment `PaymentScenariosTest.limitBelowSpent`, `LimitMathTest.usageWhenLimitIsBelowSpent` |
| 5 | Два лимита с одинаковым непустым набором категорий создать нельзя, с пустым — можно | payment `PaymentScenariosTest.duplicateLimits` |
| 6 | Хард-блок азартных игр и тумблер категории | payment `PaymentScenariosTest.hardBlockAndCategoryToggle`; smoke шаг 2 |
| 7 | Блокировка счёта: операции стоят, GPS и уроки работают | payment `PaymentScenariosTest.frozenAccountStopsMoney`; family `FamilyScenariosTest.frozenAccount` |
| 8 | Запрос, отправленный до блокировки, нельзя одобрить, пока счёт заблокирован | family `FamilyScenariosTest.approveAfterFreeze`, `paymentIsTheLastLineOfDefence` |
| 9 | P2P сверх лимита: запрос → одобрение → разовый перевод не выше одобренного | payment `PaymentScenariosTest.p2pOverLimitWithApproval`; family `FamilyScenariosTest.p2pOverRequest`; notification `NotificationTextsTest` («до 35,00 BYN»); smoke шаг 3 |
| 10 | P2P у младшего запрещён, родитель может выключить переводы подростку | payment `PaymentScenariosTest.p2pAgeAndParentSwitch` |
| 11 | Баллы ребёнка изолированы, задание приносит награду | family `FamilyScenariosTest.taskRewardAndPoints`; rewards `PointsKafkaTest.pointsAreCreditedOnce` |
| 12 | Копилка и подписки подростка | family `FamilyScenariosTest.teenPiggyDeposit`; payment `PaymentScenariosTest.teenSubscriptions` |
| 13 | Автоперевод настраивается, но не дублируется | payment `PaymentScenariosTest.autopayUpdate`, `AutopayTest.runsOncePerDay` |
| 14 | Названия периодов: «Сегодня», «Эта неделя», месяц и даты для сроков больше 1 | payment `LimitPeriodsTest.periodTitlesMatchThePrototype`, `periodStartedTwoDaysAgoIsStillRunning` |
| 15 | Траты считаются за календарный период: вчерашняя покупка не входит в «Сегодня» | payment `LimitMathTest.yesterdaySpendIsNotCountedToday` |
| 16 | Иерархия: лимит на более длинный срок не меньше более короткого | payment `PaymentScenariosTest.limitHierarchy` |
| 17 | Неделя «в руках»: покупка за баллы ребёнка, белка и кэшбэк меняются | rewards `RewardsScenariosTest.buyForChildPoints`; картинка белки — проверка фронтенда (squirrel_diamond) |
| 18 | Повторная покупка за неделю заменяет артефакт; купленный — недоступен до конца недели | rewards `RewardsScenariosTest.secondPurchaseReplaces` |
| 19 | Не хватает баллов — покупки нет; баллы работают и при заблокированном счёте | rewards `RewardsScenariosTest.notEnoughPoints` (rewards не зависит от заморозки счёта по построению) |
| 20 | Кэшбэк начисляется при оплате по QR в категории артефакта | payment `PaymentScenariosTest.cashbackOnQrPayment`; rewards `RewardsScenariosTest.buyForChildPoints` (perks); smoke шаг 4 |
| 21 | Слоты: у 7–13 четыре, у 14–17 пять (с обувью); стартовые маскоты | rewards `RewardsScenariosTest.slots`, `ArtifactRulesTest.cycleLengthAndNextSlotWeek` |
| 22 | Продление: только истёкший артефакт в неделю своего слота, −1 % до минимума 3 % | rewards `ArtifactRulesTest.renewOnlyExpiredArtifactOfTheWeekSlotMinusOnePercentDownToThree` |
| 23 | Несколько копилок: создание, пополнение, сборка → деньги на счёт | family `FamilyScenariosTest.severalPiggies`, `piggyLimit` |
| 24 | 7–13: изменение и удаление копилки — только после одобрения главного родственника | family `FamilyScenariosTest.juniorPiggyChangesNeedApproval` |
| 25 | 14–17: изменение и удаление копилки — сразу, без запросов | family `FamilyScenariosTest.teenPiggyChangesImmediate` |

Сверх спецификации: `ConcurrentPaymentsTest` (50 параллельных оплат), `IdempotencyTest`, `AccessControlTest` и
аналоги в каждом сервисе (403), Kafka-интеграция на Testcontainers (`PaymentOutboxKafkaTest`, `FamilyKafkaTest`,
`PointsKafkaTest`, `NotificationFlowTest`), `HttpPaymentClientTest` (контракт family → payment),
`FrozenProjectionOrderTest`, `GatewayRoutingTest`, `ArchitectureTest` и `ModularityTest` в каждом сервисе.

## Известные ограничения

- Стенд демонстрационный: Keycloak `start-dev`, вход через password grant (ADR-005), без TLS.
- SSE-подписки — в памяти одного инстанса notification (ADR-004).
- Трейс отправки события из outbox не связан с исходным HTTP-трейсом (ADR-008).
- Цикл недель магазина артефактов не моделируется (как в прототипе — неделя «в руках»).
- Первая сборка образов — около 10 минут (Gradle внутри Docker); дальше кэш.
- Фронтенд проверен прогоном слоя данных и рендером экранов против живого стенда, без ручного прокликивания в браузере.
