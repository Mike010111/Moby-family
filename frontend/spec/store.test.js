/**
 * Сценарные тесты бизнес-логики прототипа (без браузера): npm test
 * Исполняемая спецификация: у каждого сценария есть JUnit-аналог в services/* (см. docs/PROGRESS.md).
 * Проверяют ровно то, что жюри будет кликать руками.
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Store } from './store.js';
import { reducers } from './actions.js';
import { createInitialState } from './initial-state.js';
import { limitUsage, parseMoney, periodTitle, quoteP2P, unitStart } from '../js/core/rules.js';

const fresh = () => new Store(reducers, createInitialState);
const kid = (s) => s.getState().children.misha;
const teen = (s) => s.getState().children.ivan;

test('Запрос «5 BYN на обед» главному родственнику → одобрение меняет оба баланса и уведомляет ребёнка', () => {
  const store = fresh();
  const p0 = store.getState().parent.balance;
  const k0 = kid(store).balance;
  const req = store.dispatch('request/create', { childId: 'misha', relativeId: 'mom', amount: '5', reason: 'На обед' });
  assert.equal(req.ok, true);
  assert.equal(store.getState().requests[0].status, 'pending');
  assert.equal(store.getState().feed[0].requestId, req.id);

  assert.equal(store.dispatch('request/approve', { id: req.id }).ok, true);
  assert.equal(store.getState().parent.balance, +(p0 - 5).toFixed(2));
  assert.equal(kid(store).balance, +(k0 + 5).toFixed(2));
  assert.match(kid(store).inbox[0].text, /одобрила/);
  assert.equal(store.dispatch('request/approve', { id: req.id }).ok, false, 'повторное одобрение запрещено');
});

test('Запрос не главному родственнику уходит, но в хаб не попадает', () => {
  const store = fresh();
  const feedLen = store.getState().feed.length;
  const res = store.dispatch('request/create', { childId: 'misha', relativeId: 'dad', amount: '3', reason: 'На проезд' });
  assert.equal(res.ok, true);
  assert.equal(store.getState().requests[0].status, 'sent');
  assert.equal(store.getState().feed.length, feedLen);
});

test('Суммы — не больше двух знаков после запятой', () => {
  assert.equal(parseMoney('10,55'), 10.55);
  assert.ok(Number.isNaN(parseMoney('10.555')));
  assert.ok(Number.isNaN(parseMoney('-3')));
  const store = fresh();
  assert.equal(store.dispatch('parent/topUp', { childId: 'misha', amount: '1.234' }).ok, false);
  assert.equal(store.dispatch('parent/topUp', { childId: 'misha', amount: '1,23' }).ok, true);
});

test('Лимит меньше уже потраченного: ничего не ломается, оплата отклоняется', () => {
  const store = fresh();
  const general = kid(store).limits.find((l) => !l.categories.length);
  assert.equal(store.dispatch('limit/update', { childId: 'misha', id: general.id, count: 1, unit: 'day', amount: '1', categories: [] }).ok, true);
  const usage = limitUsage(kid(store), kid(store).limits.find((l) => l.id === general.id));
  assert.deepEqual([usage.remaining, usage.exhausted, usage.percent], [0, true, 100]);
  assert.ok(usage.overBy > 0);
  assert.equal(store.dispatch('pay/qr', { childId: 'misha', merchantId: 'm-buffet' }).code, 'LIMIT');
});

test('Два лимита с одинаковым непустым набором категорий создать нельзя, с пустым — можно', () => {
  const store = fresh();
  const base = { childId: 'misha', count: 2, unit: 'week', amount: '30' };
  assert.equal(store.dispatch('limit/create', { ...base, categories: ['cafe'] }).code, 'DUPLICATE');
  assert.equal(store.dispatch('limit/create', { ...base, categories: ['books', 'cafe'] }).ok, true);
  assert.equal(store.dispatch('limit/create', { ...base, categories: ['cafe', 'books'] }).code, 'DUPLICATE');
  assert.equal(store.dispatch('limit/create', { ...base, categories: [] }).ok, true);
  assert.equal(store.dispatch('limit/create', { ...base, count: 51, categories: [] }).code, 'COUNT');
  assert.equal(store.dispatch('limit/create', { ...base, categories: ['p2p'] }).code, 'CATEGORY', 'у младшего нет категории «Переводы»');
});

test('Хард-блок азартных игр и тумблер категории', () => {
  const store = fresh();
  assert.equal(store.dispatch('pay/qr', { childId: 'misha', merchantId: 'm-slots' }).code, 'HARD_BLOCK');
  assert.equal(store.dispatch('pay/qr', { childId: 'misha', merchantId: 'm-burger' }).code, 'PARENT_CATEGORY');
  store.dispatch('parent/toggleAllow', { childId: 'misha', key: 'fastfood' });
  const ok = store.dispatch('pay/qr', { childId: 'misha', merchantId: 'm-burger' });
  assert.equal(ok.ok, true);
  assert.match(ok.legal, /ст\. 27 ГК РБ/);
});

test('Блокировка счёта: операции стоят, GPS и уроки работают', () => {
  const store = fresh();
  store.dispatch('parent/toggleFreeze', { childId: 'misha' });
  assert.equal(store.dispatch('parent/topUp', { childId: 'misha', amount: '5' }).code, 'FROZEN');
  assert.equal(store.dispatch('piggy/deposit', { childId: 'misha', piggyId: 'pg-bike', amount: '1' }).code, 'FROZEN');
  assert.equal(store.dispatch('task/complete', { childId: 'misha', taskId: 't1' }).code, 'FROZEN');
  assert.equal(store.dispatch('parent/addTask', { childId: 'misha', title: 'Тест', reward: '1' }).code, 'FROZEN');
  assert.equal(store.dispatch('pay/qr', { childId: 'misha', merchantId: 'm-buffet' }).code, 'FROZEN');
  assert.equal(store.dispatch('request/create', { childId: 'misha', relativeId: 'mom', amount: '5' }).code, 'FROZEN');
  assert.equal(store.dispatch('parent/refreshGeo', { childId: 'misha' }).ok, true);
  assert.equal(store.dispatch('lesson/complete', { childId: 'misha' }).ok, true);
});

test('Запрос, отправленный до блокировки, нельзя одобрить, пока счёт заблокирован', () => {
  const store = fresh();
  const req = store.dispatch('request/create', { childId: 'misha', relativeId: 'mom', amount: '5', reason: 'На обед' });
  store.dispatch('parent/toggleFreeze', { childId: 'misha' });
  assert.equal(store.dispatch('request/approve', { id: req.id }).code, 'FROZEN');
});

test('P2P сверх лимита: запрос → одобрение → разовый перевод не выше одобренного', () => {
  const store = fresh();
  assert.equal(quoteP2P(teen(store), 10).mode, 'send');
  assert.equal(quoteP2P(teen(store), 35).mode, 'request');
  assert.equal(store.dispatch('p2p/send', { childId: 'ivan', contactId: 'c-katya', amount: '35' }).code, 'NEEDS_APPROVAL');

  assert.equal(store.dispatch('p2p/requestOver', { childId: 'ivan', contactId: 'c-katya', amount: '999' }).code, 'FUNDS');
  const r = store.dispatch('p2p/requestOver', { childId: 'ivan', contactId: 'c-katya', amount: '35' });
  assert.equal(r.ok, true);
  const pending = store.getState().requests.find((x) => x.kind === 'p2p-over' && x.status === 'pending');
  store.dispatch('request/approve', { id: pending.id });
  assert.match(teen(store).inbox[0].text, /до 35,00/);

  assert.equal(quoteP2P(teen(store), 40).mode, 'request', 'выше одобренной суммы нельзя');
  assert.equal(quoteP2P(teen(store), 10).mode, 'send', 'в пределах лимита одобрение не тратится');
  const before = teen(store).balance;
  assert.equal(store.dispatch('p2p/send', { childId: 'ivan', contactId: 'c-katya', amount: '30' }).mode, 'approved');
  assert.equal(teen(store).balance, +(before - 30).toFixed(2));
  assert.equal(quoteP2P(teen(store), 30).mode, 'request', 'одобрение разовое');
});

test('P2P у младшего запрещён, родитель может выключить переводы подростку', () => {
  const store = fresh();
  assert.equal(store.dispatch('parent/toggleAllow', { childId: 'misha', key: 'p2p' }).ok, false);
  store.dispatch('parent/toggleAllow', { childId: 'ivan', key: 'p2p' });
  assert.equal(store.dispatch('p2p/send', { childId: 'ivan', contactId: 'c-katya', amount: '1' }).code, 'PARENT_P2P');
});

test('Баллы ребёнка изолированы, задание приносит награду', () => {
  const store = fresh();
  const pp = store.getState().parent.points;
  store.dispatch('lesson/complete', { childId: 'misha' });
  store.dispatch('task/complete', { childId: 'misha', taskId: 't1' });
  const b = kid(store).balance;
  store.dispatch('task/approve', { childId: 'misha', taskId: 't1' });
  assert.equal(store.getState().parent.points, pp);
  assert.equal(kid(store).balance, +(b + 2).toFixed(2));
});

test('Копилка и подписки подростка', () => {
  const store = fresh();
  assert.equal(store.dispatch('piggy/deposit', { childId: 'ivan', piggyId: 'pg-laptop', amount: '10,50' }).ok, true);
  assert.equal(teen(store).piggies[0].saved, 320.5);
  assert.equal(store.dispatch('sub/update', { childId: 'ivan', id: 'sub1', price: '7,49' }).ok, true);
  store.dispatch('sub/toggle', { childId: 'ivan', id: 'sub1' });
  assert.equal(teen(store).subscriptions[0].active, false);
  store.dispatch('sub/delete', { childId: 'ivan', id: 'sub1' });
  assert.equal(teen(store).subscriptions.length, 2);
});

test('Автоперевод настраивается, но не дублируется', () => {
  const store = fresh();
  const res = store.dispatch('autopay/update', { childId: 'misha', enabled: true, amount: '12,5', every: 'month', monthDay: 5 });
  assert.equal(res.ok, true);
  assert.deepEqual(kid(store).autopay, { enabled: true, amount: 12.5, every: 'month', weekday: 0, monthDay: 5 });
});

test('Названия периодов: «Сегодня», «Эта неделя», месяц и даты для сроков больше 1', () => {
  const now = new Date(2026, 9, 21, 12).getTime(); // среда, 21 октября 2026
  const at = (count, unit) => ({ count, unit, start: unitStart(now, unit) });
  assert.equal(periodTitle(at(1, 'day'), now), 'Сегодня');
  assert.equal(periodTitle(at(1, 'week'), now), 'Эта неделя');
  assert.equal(periodTitle(at(1, 'month'), now), 'Октябрь');
  assert.equal(periodTitle(at(3, 'day'), now), '21–23 октября');
  assert.equal(periodTitle(at(2, 'week'), now), '19 окт – 1 ноя');
  assert.equal(periodTitle(at(3, 'month'), now), 'Октябрь – Декабрь');
  // период, начатый 2 дня назад на 3 дня, — ещё идёт
  assert.equal(periodTitle({ count: 3, unit: 'day', start: unitStart(now - 2 * 86_400_000, 'day') }, now), '19–21 октября');
});

test('Траты считаются за календарный период: вчерашняя покупка не входит в «Сегодня»', () => {
  const now = new Date(2026, 9, 21, 9).getTime();
  const child = { tx: [{ amount: -5, mcc: '5462', ts: now - 10 * 3_600_000, status: 'ok' }] };
  const limit = { count: 1, unit: 'day', amount: 10, categories: [], start: unitStart(now, 'day') };
  assert.equal(limitUsage(child, limit, now).spent, 0);
});

test('Иерархия: лимит на более длинный срок не меньше более короткого, если покрывает его категории', () => {
  const store = fresh();
  const base = { childId: 'misha', categories: [] };
  // У Миши: «Сегодня · все» 15 BYN и «Эта неделя · Кафе» 20 BYN
  assert.equal(store.dispatch('limit/create', { ...base, count: 1, unit: 'week', amount: '10' }).code, 'HIERARCHY', 'неделя (все) < дня (все)');
  assert.equal(store.dispatch('limit/create', { ...base, count: 1, unit: 'week', amount: '15' }).ok, true, 'равно — можно');
  assert.equal(store.dispatch('limit/create', { ...base, count: 1, unit: 'month', amount: '14' }).code, 'HIERARCHY', 'месяц < недели');
  assert.equal(store.dispatch('limit/create', { ...base, categories: ['books'], count: 3, unit: 'day', amount: '25' }).code, 'HIERARCHY', '3 дня «Книги» > недели «все категории»');
  assert.equal(store.dispatch('limit/create', { ...base, categories: ['books'], count: 1, unit: 'month', amount: '5' }).ok, true, 'месяц «Книги» не обязан превышать неделю «Кафе»');
  const day = kid(store).limits.find((l) => l.unit === 'day');
  assert.equal(store.dispatch('limit/update', { childId: 'misha', id: day.id, count: 1, unit: 'day', amount: '16', categories: [] }).code, 'HIERARCHY', 'день не может стать больше недели');
});

/* ───────────── Артефакты маскота ───────────── */
import { SLOTS, cashbackFor, mascotImage, offerState, renewOffer } from '../js/core/artifacts.js';

test('Неделя «в руках»: покупка за баллы ребёнка, белка и кэшбэк меняются', () => {
  const store = fresh();
  const p0 = kid(store).points;
  assert.equal(kid(store).artifacts.weekSlot, 'hands');
  assert.match(mascotImage(kid(store)).src, /squirrel1\.webp$/);

  const res = store.dispatch('artifact/buy', { childId: 'misha', id: 'diamond' });
  assert.equal(res.ok, true);
  assert.equal(kid(store).points, p0 - 200);
  assert.match(mascotImage(kid(store)).src, /squirrel_diamond\.webp$/);
  assert.equal(cashbackFor(kid(store), 'cafe').cashback, 5);
  assert.equal(store.getState().parent.points, 3420, 'баллы родителя не тратятся');
});

test('Повторная покупка за неделю заменяет артефакт; купленный — недоступен до конца недели', () => {
  const store = fresh();
  store.dispatch('artifact/buy', { childId: 'misha', id: 'nut' });
  const r = store.dispatch('artifact/buy', { childId: 'misha', id: 'coin' });
  assert.equal(r.replaced, 'Лесной орех');
  assert.equal(offerState(kid(store), 'nut').status, 'soldout');
  assert.equal(offerState(kid(store), 'coin').status, 'equipped');
  assert.equal(store.dispatch('artifact/buy', { childId: 'misha', id: 'nut' }).code, 'SOLDOUT');
  assert.equal(cashbackFor(kid(store), 'food'), null, 'кэшбэк ореха пропал вместе с ним');
  assert.equal(cashbackFor(kid(store), 'books').cashback, 4);
});

test('Не хватает баллов — покупки нет; баллы работают и при заблокированном счёте', () => {
  const store = fresh();
  store.dispatch('artifact/buy', { childId: 'misha', id: 'diamond' }); // 320 → 120
  assert.equal(store.dispatch('artifact/buy', { childId: 'misha', id: 'coin' }).ok, true); // 120 → 20
  assert.equal(store.dispatch('artifact/buy', { childId: 'misha', id: 'gamepad' }).code, 'POINTS');
  const s2 = fresh();
  s2.dispatch('parent/toggleFreeze', { childId: 'misha' });
  assert.equal(s2.dispatch('artifact/buy', { childId: 'misha', id: 'nut' }).ok, true);
});

test('Кэшбэк начисляется при оплате по QR в категории артефакта', () => {
  const store = fresh();
  store.dispatch('artifact/buy', { childId: 'misha', id: 'diamond' }); // 5% «Кафе и столовые»
  const b0 = kid(store).balance;
  const res = store.dispatch('pay/qr', { childId: 'misha', merchantId: 'm-buffet' }); // 2,40 — кафе
  assert.deepEqual([res.cashback.pct, res.cashback.value], [5, 0.12]);
  assert.equal(kid(store).balance, +(b0 - 2.4 + 0.12).toFixed(2));
  assert.equal(kid(store).tx[0].mcc, 'cashback');
});

test('Слоты: у 7–13 четыре, у 14–17 пять (с обувью); стартовые маскоты', () => {
  const store = fresh();
  assert.deepEqual(Object.keys(kid(store).artifacts.owned), ['hat', 'glasses', 'outer', 'hands']);
  assert.deepEqual(Object.keys(teen(store).artifacts.owned), ['hat', 'glasses', 'outer', 'hands', 'shoes']);
  assert.ok(SLOTS.shoes);
  assert.match(mascotImage(teen(store)).src, /squirrel_brown\.webp$/);
  store.dispatch('artifact/buy', { childId: 'ivan', id: 'nut' });
  assert.deepEqual(mascotImage(teen(store)), { src: 'assets/mascot/squirrel_brown_nut.webp' });
});

test('Продление: только истёкший артефакт в неделю своего слота, −1 % до минимума 3 %', () => {
  const store = fresh();
  store.dispatch('artifact/buy', { childId: 'misha', id: 'diamond' });
  assert.equal(renewOffer(kid(store)), null, 'пока действует — продлевать нечего');
  const c = structuredClone(kid(store));
  c.artifacts.owned.hands.activeUntil = Date.now() - 1; // цикл закончился, снова неделя «в руках»
  const o = renewOffer(c);
  assert.deepEqual([o.cashback, o.rarity, o.price], [4, 'rare', 100]);
  c.artifacts.owned.hands.cashback = 3;
  assert.deepEqual([renewOffer(c).cashback, renewOffer(c).price], [3, 50]);
});

/* ───────────── Копилки ───────────── */

test('Несколько копилок: создание, пополнение, сборка → деньги на счёт', () => {
  const store = fresh();
  assert.equal(store.dispatch('piggy/create', { childId: 'misha', goal: 'Конструктор', target: '8' }).ok, true);
  assert.equal(store.dispatch('piggy/create', { childId: 'misha', goal: 'конструктор', target: '5' }).ok, false, 'дубликат названия');
  const p = kid(store).piggies.find((x) => x.goal === 'Конструктор');
  const b0 = kid(store).balance;
  assert.equal(store.dispatch('piggy/deposit', { childId: 'misha', piggyId: p.id, amount: '9' }).ok, false, 'больше цели нельзя');
  const res = store.dispatch('piggy/deposit', { childId: 'misha', piggyId: p.id, amount: '4' });
  assert.deepEqual([res.ok, res.completed], [true, null]);
  const done = store.dispatch('piggy/deposit', { childId: 'misha', piggyId: p.id, amount: '4' });
  assert.deepEqual(done.completed, { goal: 'Конструктор', amount: 8 });
  assert.equal(kid(store).balance, b0, 'собранные деньги вернулись на счёт');
  assert.equal(kid(store).piggies.some((x) => x.id === p.id), false);
  assert.match(kid(store).inbox[0].text, /собрана/);
});

test('7–13: изменение и удаление копилки — только после одобрения главного родственника', () => {
  const store = fresh();
  const upd = store.dispatch('piggy/update', { childId: 'misha', piggyId: 'pg-bike', target: '300' });
  assert.equal(upd.pending, true);
  assert.equal(kid(store).piggies[0].target, 250, 'пока не одобрено — не меняется');
  assert.equal(store.dispatch('piggy/delete', { childId: 'misha', piggyId: 'pg-bike' }).code, 'PENDING');
  const req = store.getState().requests.find((r) => r.kind === 'piggy-update');
  store.dispatch('request/approve', { id: req.id });
  assert.equal(kid(store).piggies[0].target, 300);

  assert.equal(store.dispatch('piggy/update', { childId: 'misha', piggyId: 'pg-bike', target: '50' }).code, 'BELOW_SAVED');
  const b0 = kid(store).balance;
  store.dispatch('piggy/delete', { childId: 'misha', piggyId: 'pg-bike' });
  const del = store.getState().requests.find((r) => r.kind === 'piggy-delete');
  store.dispatch('request/decline', { id: del.id });
  assert.equal(kid(store).piggies.length, 1, 'отказ — копилка остаётся');
  store.dispatch('piggy/delete', { childId: 'misha', piggyId: 'pg-bike' });
  store.dispatch('request/approve', { id: store.getState().requests[0].id });
  assert.equal(kid(store).piggies.length, 0);
  assert.equal(kid(store).balance, +(b0 + 86.5).toFixed(2));
});

test('14–17: изменение и удаление копилки — сразу, без запросов', () => {
  const store = fresh();
  const n = store.getState().requests.length;
  assert.equal(store.dispatch('piggy/update', { childId: 'ivan', piggyId: 'pg-laptop', target: '1500' }).ok, true);
  assert.equal(teen(store).piggies[0].target, 1500);
  const done = store.dispatch('piggy/update', { childId: 'ivan', piggyId: 'pg-concert', target: '52' });
  assert.equal(done.completed.amount, 52, 'цель = собранному → копилка собрана');
  const b0 = teen(store).balance;
  assert.equal(store.dispatch('piggy/delete', { childId: 'ivan', piggyId: 'pg-laptop' }).returned, 310);
  assert.equal(teen(store).balance, +(b0 + 310).toFixed(2));
  assert.equal(store.getState().requests.length, n);
});
