/**
 * Артефакты маскота — чистые правила и справочники (без побочных эффектов).
 *
 * Механика:
 *  - у 7–13 четыре слота (головной убор → очки → верхняя одежда → в руках), цикл 4 недели;
 *    у 14–17 добавляется обувь — пять слотов, цикл 5 недель;
 *  - каждую неделю в магазине продаются артефакты только одного слота — по очереди;
 *  - купленный артефакт действует один цикл: до начала следующей недели своего слота;
 *  - в неделю своего слота истёкший артефакт можно продлить: кэшбэк падает на 1 %
 *    (но не ниже 3 %), цена — по редкости, соответствующей новому кэшбэку;
 *  - за неделю можно купить несколько артефактов одного слота: новый заменяет прежний,
 *    а купленный на этой неделе становится недоступен до её конца.
 *
 * Время циклов в прототипе не моделируется: демо-неделя — «вещи в руках».
 */
import { category, round2, unitStart } from './rules.js';

const DAY = 86_400_000;
const WEEK = 7 * DAY;

export const SLOTS = {
  hat: { label: 'Головной убор', week: 'Неделя головных уборов', next: 'головные уборы', icon: 'hat' },
  glasses: { label: 'Очки', week: 'Неделя очков', next: 'очки', icon: 'glasses' },
  outer: { label: 'Верхняя одежда', week: 'Неделя верхней одежды', next: 'верхняя одежда', icon: 'shirt' },
  hands: { label: 'В руках', week: 'Неделя вещей в руках', next: 'вещи в руках', icon: 'hand' },
  shoes: { label: 'Обувь', week: 'Неделя обуви', next: 'обувь', icon: 'shoe' },
};

/** Порядок недель в цикле. Длина цикла в неделях = количество слотов. */
export const CYCLE = {
  junior: ['hat', 'glasses', 'outer', 'hands'],
  teen: ['hat', 'glasses', 'outer', 'hands', 'shoes'],
};

/** Редкость → базовый кэшбэк и цена в баллах. Продление стоит по редкости нового кэшбэка. */
export const RARITY = {
  common: { label: 'Обычный', cashback: 3, price: 50 },
  rare: { label: 'Редкий', cashback: 4, price: 100 },
  legendary: { label: 'Легендарный', cashback: 5, price: 200 },
};
export const MIN_CASHBACK = 3;

export const rarityByCashback = (pct) => (pct >= 5 ? 'legendary' : pct >= 4 ? 'rare' : 'common');

/**
 * Каталог артефактов. category — на какие покупки действует кэшбэк;
 * для разных возрастов одна и та же вещь может давать кэшбэк на разное.
 */
export const ARTIFACTS = {
  nut: { slot: 'hands', name: 'Лесной орех', rarity: 'common', category: { junior: 'food', teen: 'food' } },
  coin: { slot: 'hands', name: 'Монета 2 рубля', rarity: 'rare', category: { junior: 'books', teen: 'transport' } },
  gamepad: { slot: 'hands', name: 'Геймпад', rarity: 'rare', category: { junior: 'entertainment', teen: 'subscriptions' } },
  diamond: { slot: 'hands', name: 'Бриллиант', rarity: 'legendary', category: { junior: 'cafe', teen: 'cafe' } },
  'glasses-blue': { slot: 'glasses', name: 'Синие очки', rarity: 'rare', category: { junior: 'transport', teen: 'transport' } },
  'glasses-dark': { slot: 'glasses', name: 'Тёмные очки', rarity: 'common', category: { junior: 'sport', teen: 'sport' } },
  'vest-brown': { slot: 'outer', name: 'Коричневый жилет', rarity: 'rare', category: { junior: 'clothes', teen: 'clothes' } },
};

/** Что продаётся на неделе слота (в прототипе заготовлена только неделя «в руках»). */
export const SHOP = {
  hands: ['nut', 'coin', 'gamepad', 'diamond'],
};

/** Экземпляр артефакта у ребёнка — снимок с собственным кэшбэком и сроком. */
export function makeOwned(id, cohort, { boughtAt, activeUntil, cashback, cycles = 1 }) {
  const a = ARTIFACTS[id];
  return {
    id,
    slot: a.slot,
    name: a.name,
    rarity: a.rarity,
    category: a.category[cohort],
    cashback: cashback ?? RARITY[a.rarity].cashback,
    cycles,
    boughtAt,
    activeUntil,
  };
}

export const cycleWeeks = (cohort) => CYCLE[cohort].length;

/** Начало ближайшей (следующей) недели слота относительно текущей недели цикла. */
export function nextSlotWeekStart(art, slot) {
  const order = CYCLE[art.cohort];
  const cur = order.indexOf(art.weekSlot);
  const target = order.indexOf(slot);
  const diff = (target - cur + order.length) % order.length || order.length;
  return art.weekStart + diff * WEEK;
}

/**
 * Исходное состояние артефактов: текущая неделя цикла — «в руках».
 * owned: { slot: { id, boughtWeeksAgo } } — артефакты, купленные в прошлые недели этого цикла.
 */
export function artifactsState(now, cohort, owned = {}) {
  const weekStart = unitStart(now, 'week');
  const art = { cohort, weekSlot: 'hands', weekStart, owned: {}, soldOut: [] };
  for (const slot of CYCLE[cohort]) art.owned[slot] = null;
  for (const [slot, { id, boughtWeeksAgo }] of Object.entries(owned)) {
    art.owned[slot] = makeOwned(id, cohort, {
      boughtAt: weekStart - boughtWeeksAgo * WEEK,
      activeUntil: nextSlotWeekStart(art, slot),
    });
  }
  return art;
}

export const weekEnd = (art) => art.weekStart + WEEK;
export const isActive = (owned, now = Date.now()) => !!owned && owned.activeUntil > now;

/** Следующий слот после текущей недели. */
export function nextSlot(art) {
  const order = CYCLE[art.cohort];
  return order[(order.indexOf(art.weekSlot) + 1) % order.length];
}

/** Состояние предложения в магазине для ребёнка. */
export function offerState(child, id, now = Date.now()) {
  const art = child.artifacts;
  const a = ARTIFACTS[id];
  const price = RARITY[a.rarity].price;
  const current = art.owned[a.slot];
  if (isActive(current, now) && current.id === id) return { status: 'equipped', price };
  if (art.soldOut.includes(id)) return { status: 'soldout', price };
  if (child.points < price) return { status: 'nopoints', price, lack: price - child.points };
  return { status: 'available', price, replaces: isActive(current, now) ? current : null };
}

/**
 * Предложение продлить свой артефакт: только в неделю его слота и только если цикл истёк.
 * Кэшбэк −1 % за цикл, но не ниже 3 %; цена — по редкости нового кэшбэка.
 */
export function renewOffer(child, now = Date.now()) {
  const art = child.artifacts;
  const own = art.owned[art.weekSlot];
  if (!own || isActive(own, now) || now >= weekEnd(art)) return null;
  const cashback = Math.max(MIN_CASHBACK, own.cashback - 1);
  const rarity = rarityByCashback(cashback);
  return { artifact: own, cashback, rarity, price: RARITY[rarity].price };
}

/** Лучший действующий кэшбэк на категорию покупки. */
export function cashbackFor(child, categoryKey, now = Date.now()) {
  let best = null;
  for (const own of Object.values(child.artifacts?.owned ?? {})) {
    if (isActive(own, now) && own.category === categoryKey && (!best || own.cashback > best.cashback)) best = own;
  }
  return best;
}

export const cashbackAmount = (amount, pct) => round2(Math.max(0.01, (amount * pct) / 100));

export const categoryLabel = (key) => category(key)?.label ?? key;

/* ───────────── Изображения ───────────── */

const MASCOT_BASE = { junior: 'squirrel', teen: 'squirrel_brown' };
const FIRST_IMAGE = { junior: 'squirrel1', teen: 'squirrel_brown' };

/**
 * Картинка маскота по надетым артефактам. В прототипе меняется слот «в руках».
 * fallback — запасная картинка, если файла ещё нет (например, squirrel_brown_nut).
 */
export function mascotImage(child, now = Date.now()) {
  const cohort = child.cohort;
  const hands = child.artifacts.owned.hands;
  if (!isActive(hands, now)) return { src: `assets/mascot/${FIRST_IMAGE[cohort]}.webp` };
  return { src: `assets/mascot/${MASCOT_BASE[cohort]}_${hands.id}.webp` };
}

export const artifactImage = (id) => `assets/artifacts/artifact_${id}.webp`;

/**
 * Все картинки карты и магазина: маскоты обоих возрастов (без артефакта и с каждой вещью
 * «в руках») и сами артефакты. main.js загружает и декодирует их заранее, поэтому
 * смена артефакта, вкладки или открытие магазина не ждут сети.
 */
export function imageUrls() {
  const hands = Object.keys(ARTIFACTS).filter((id) => ARTIFACTS[id].slot === 'hands');
  return [
    ...Object.values(FIRST_IMAGE).map((f) => `assets/mascot/${f}.webp`),
    ...Object.values(MASCOT_BASE).flatMap((base) => hands.map((id) => `assets/mascot/${base}_${id}.webp`)),
    ...hands.map(artifactImage),
  ];
}
