# Исполняемая спецификация прототипа

До бэкенда вся логика жила в браузере: `actions.js` (редьюсеры), `store.js`, `initial-state.js`. Теперь это
**спецификация поведения**: бэкенд обязан вести себя так же, а без бэкенда (GitHub Pages) эти же файлы работают в
браузере как офлайн-демо. `npm test` прогоняет `store.test.js` (сценарии) и `rules.test.js` (расчёты: округление,
«Доступно» в сканере QR, котировка P2P).

## Сценарии и их JUnit-аналоги

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
