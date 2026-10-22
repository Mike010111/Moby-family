/**
 * Бизнес-правила семейного пакета — чистые функции без побочных эффектов.
 * Всё, что можно проверить без браузера, живёт здесь.
 */
import { money, plural } from './format.js';

const DAY = 86_400_000;

/* ───────────── Категории ───────────── */

/**
 * Популярные категории трат. key — то, чем управляет родитель тумблером
 * и что выбирается в лимите. mcc — коды торговых точек этой категории.
 * channel: true — это канал оплаты (интернет), а не тип точки.
 * minCohort: 'teen' — доступно только с 14 лет.
 */
export const CATEGORIES = [
  { key: 'food', label: 'Продукты и супермаркеты', icon: 'cart', mcc: ['5411'] },
  { key: 'cafe', label: 'Кафе и столовые', icon: 'coffee', mcc: ['5812', '5462'] },
  { key: 'fastfood', label: 'Фастфуд', icon: 'burger', mcc: ['5814'] },
  { key: 'transport', label: 'Транспорт и такси', icon: 'bus', mcc: ['4111', '4121'] },
  { key: 'clothes', label: 'Одежда и обувь', icon: 'shirt', mcc: ['5651'] },
  { key: 'electronics', label: 'Электроника', icon: 'phone', mcc: ['5732'] },
  { key: 'books', label: 'Книги и канцтовары', icon: 'book', mcc: ['5942', '5943'] },
  { key: 'education', label: 'Образование и кружки', icon: 'grad', mcc: ['8299'] },
  { key: 'sport', label: 'Спорт', icon: 'dumbbell', mcc: ['5941'] },
  { key: 'pharmacy', label: 'Аптеки', icon: 'pill', mcc: ['5912'] },
  { key: 'games', label: 'Игры и донаты', icon: 'gamepad', mcc: ['5816'] },
  { key: 'entertainment', label: 'Кино и развлечения', icon: 'film', mcc: ['7832'] },
  { key: 'subscriptions', label: 'Подписки и сервисы', icon: 'music', mcc: ['5735', '5815'] },
  { key: 'online', label: 'Покупки в интернете', icon: 'globe', mcc: [], channel: true },
  { key: 'cash', label: 'Снятие наличных', icon: 'banknote', mcc: ['6011'] },
  { key: 'p2p', label: 'Переводы', icon: 'send', mcc: ['4829'], minCohort: 'teen' },
];

/** Банковские хард-блоки — родитель не может их включить. */
export const HARD_BLOCKS = [
  { key: 'gambling', label: 'Азартные игры и ставки', icon: 'dice', mcc: ['7995'] },
  { key: 'adult', label: 'Категория 18+', icon: 'lock', mcc: ['5967'] },
];

const BY_KEY = new Map([...CATEGORIES, ...HARD_BLOCKS].map((c) => [c.key, c]));
const BY_MCC = new Map();
for (const c of [...CATEGORIES, ...HARD_BLOCKS]) for (const m of c.mcc) BY_MCC.set(m, c);

export const category = (key) => BY_KEY.get(key);

/** Группа (ключ категории) по MCC. Служебные операции — 'topup' / 'piggy'. */
export function groupOf(mcc) {
  if (mcc === 'topup' || mcc === 'piggy' || mcc === 'cashback') return mcc;
  return BY_MCC.get(mcc)?.key ?? 'other';
}

const SERVICE_LABELS = { piggy: 'Копилка', topup: 'Пополнение', cashback: 'Кэшбэк' };
export const groupLabel = (mcc) => BY_MCC.get(mcc)?.label ?? SERVICE_LABELS[mcc] ?? 'Прочее';

/** Категории, которые применимы к возрасту ребёнка. */
export const categoriesFor = (child) => CATEGORIES.filter((c) => !c.minCohort || child.cohort === c.minCohort);

/** Категории, «открытые» для счёта (разрешены родителем) — из них собирается лимит. */
export const openCategories = (child) => categoriesFor(child).filter((c) => child.allow[c.key]);

/* ───────────── Деньги ───────────── */

export const round2 = (n) => Math.round(n * 100) / 100;

/** Сумма: положительное число, не больше двух знаков после запятой. Принимает и «5,25». */
export function parseMoney(raw) {
  const s = String(raw ?? '').trim().replace(',', '.');
  if (!/^\d+(\.\d{1,2})?$/.test(s)) return NaN;
  return Number(s);
}

/* ───────────── Лимиты ───────────── */

export const LIMIT_UNITS = {
  day: { label: 'День', days: 1, forms: ['день', 'дня', 'дней'] },
  week: { label: 'Неделя', days: 7, forms: ['неделя', 'недели', 'недель'] },
  month: { label: 'Месяц', days: 30, forms: ['месяц', 'месяца', 'месяцев'] },
};
export const LIMIT_MAX_COUNT = 50;

/** Длительность срока — «1 неделя», «3 дня». Используется в окне настройки лимита. */
export const periodLabel = ({ count, unit }) => `${count} ${plural(count, LIMIT_UNITS[unit].forms)}`;

const MONTHS = ['Январь', 'Февраль', 'Март', 'Апрель', 'Май', 'Июнь', 'Июль', 'Август', 'Сентябрь', 'Октябрь', 'Ноябрь', 'Декабрь'];
const MONTHS_GEN = ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня', 'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря'];
const MONTHS_SHORT = ['янв', 'фев', 'мар', 'апр', 'мая', 'июн', 'июл', 'авг', 'сен', 'окт', 'ноя', 'дек'];

/** Начало календарной единицы: полночь, понедельник этой недели, 1-е число месяца. */
export function unitStart(ts, unit) {
  const d = new Date(ts);
  d.setHours(0, 0, 0, 0);
  if (unit === 'week') d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
  if (unit === 'month') d.setDate(1);
  return d.getTime();
}

function addUnits(ts, unit, n) {
  const d = new Date(ts);
  if (unit === 'day') d.setDate(d.getDate() + n);
  else if (unit === 'week') d.setDate(d.getDate() + 7 * n);
  else d.setMonth(d.getMonth() + n);
  return d.getTime();
}

function unitsBetween(from, to, unit) {
  if (unit === 'month') {
    const a = new Date(from);
    const b = new Date(to);
    return (b.getFullYear() - a.getFullYear()) * 12 + b.getMonth() - a.getMonth();
  }
  const days = Math.round((to - from) / DAY); // round — устойчиво к переходу на летнее время
  return unit === 'week' ? Math.floor(days / 7) : days;
}

/**
 * Текущий календарный период лимита [from, to).
 * Периоды по count единиц идут подряд от limit.start (начало дня / недели / месяца при создании).
 */
export function limitPeriod(limit, now = Date.now()) {
  const anchor = limit.start ?? unitStart(now, limit.unit);
  const elapsed = Math.max(0, unitsBetween(anchor, unitStart(now, limit.unit), limit.unit));
  const from = addUnits(anchor, limit.unit, elapsed - (elapsed % limit.count));
  return { from, to: addUnits(from, limit.unit, limit.count) };
}

/** Название текущего периода: «Сегодня», «Эта неделя», «Октябрь»; для сроков > 1 — даты: «21–23 октября». */
export function periodTitle(limit, now = Date.now()) {
  const { from, to } = limitPeriod(limit, now);
  const a = new Date(from);
  if (limit.count === 1) {
    if (limit.unit === 'day') return 'Сегодня';
    if (limit.unit === 'week') return 'Эта неделя';
    return MONTHS[a.getMonth()];
  }
  const b = new Date(addUnits(to, 'day', -1)); // последний день периода
  if (limit.unit === 'month') return `${MONTHS[a.getMonth()]} – ${MONTHS[b.getMonth()]}`;
  if (a.getMonth() === b.getMonth()) return `${a.getDate()}–${b.getDate()} ${MONTHS_GEN[a.getMonth()]}`;
  return `${a.getDate()} ${MONTHS_SHORT[a.getMonth()]} – ${b.getDate()} ${MONTHS_SHORT[b.getMonth()]}`;
}

export function limitCategoriesLabel(limit) {
  if (!limit.categories.length) return 'Все категории';
  return limit.categories.map((k) => category(k)?.label ?? k).join(', ');
}

/** Ключ множества категорий — для проверки уникальности (порядок не важен). */
const setKey = (cats) => [...cats].sort().join('|');

/** Попадает ли операция под лимит. Пустой список категорий = все траты. */
export function txMatchesLimit(tx, limit) {
  const g = groupOf(tx.mcc);
  if (g === 'topup' || g === 'piggy' || g === 'cashback') return false;
  if (!limit.categories.length) return true;
  return limit.categories.includes(g) || (tx.online === true && limit.categories.includes('online'));
}

/**
 * Состояние лимита за текущий календарный период. Корректно работает, даже если лимит
 * выставлен меньше уже потраченного: remaining = 0, exhausted = true, overBy > 0.
 */
export function limitUsage(child, limit, now = Date.now()) {
  const { from, to } = limitPeriod(limit, now);
  let spent = 0;
  for (const t of child.tx) {
    if (t.status !== 'ok' || t.amount >= 0 || t.ts < from || t.ts >= to) continue;
    if (txMatchesLimit(t, limit)) spent += -t.amount;
  }
  spent = round2(spent);
  const remaining = round2(Math.max(0, limit.amount - spent));
  const exhausted = spent >= limit.amount;
  return {
    spent,
    remaining,
    exhausted,
    overBy: round2(Math.max(0, spent - limit.amount)),
    percent: limit.amount > 0 ? Math.min(100, Math.round((spent / limit.amount) * 100)) : 100,
  };
}

/** Самый жёсткий из лимитов, под которые попадает будущая операция. */
export function tightestLimit(child, op, now = Date.now()) {
  let best = null;
  for (const limit of child.limits) {
    if (!txMatchesLimit(op, limit)) continue;
    const usage = limitUsage(child, limit, now);
    if (!best || usage.remaining < best.usage.remaining) best = { limit, usage };
  }
  return best;
}

/** Длина срока в днях — для сравнения «короче / длиннее» (месяц считается за 30 дней). */
const periodDays = (l) => l.count * LIMIT_UNITS[l.unit].days;

/** Покрывает ли набор категорий outer все траты набора inner. Пустой набор = все категории. */
const covers = (outer, inner) => !outer.length || (inner.length > 0 && inner.every((k) => outer.includes(k)));

/**
 * Валидация лимита при создании/изменении.
 *
 * Иерархия сумм: если лимит B длиннее лимита A и B распространяется на все категории A,
 * то B ≥ A. Иначе короткий лимит теряет смысл (неделя 20 BYN при дне 30 BYN — день никогда не сработает).
 * Лимиты на непересекающиеся категории (день «Кафе» и неделя «Книги») друг друга не ограничивают.
 *
 * @param {object} child
 * @param {{count, unit, amount, categories}} draft
 * @param {string} [selfId] id редактируемого лимита (исключается из проверок)
 */
export function validateLimit(child, draft, selfId, now = Date.now()) {
  const count = Number(draft.count);
  if (!Number.isInteger(count) || count < 1 || count > LIMIT_MAX_COUNT) return deny('COUNT', `Срок — целое число от 1 до ${LIMIT_MAX_COUNT}`);
  if (!LIMIT_UNITS[draft.unit]) return deny('UNIT', 'Выберите день, неделю или месяц');
  const amount = parseMoney(draft.amount);
  if (!(amount > 0)) return deny('AMOUNT', 'Укажите сумму — не больше двух знаков после запятой');
  const allowed = new Set(categoriesFor(child).map((c) => c.key));
  const cats = [...new Set(draft.categories ?? [])];
  if (cats.some((k) => !allowed.has(k))) return deny('CATEGORY', 'Эта категория недоступна для счёта');
  if (cats.length) {
    const key = setKey(cats);
    const dup = child.limits.find((l) => l.id !== selfId && l.categories.length && setKey(l.categories) === key);
    if (dup) return deny('DUPLICATE', 'Лимит с таким же набором категорий уже есть');
  }

  const me = { count, unit: draft.unit, categories: cats };
  const others = child.limits.filter((l) => l.id !== selfId);
  const name = (l) => `«${periodTitle(l, now)} · ${limitCategoriesLabel(l)}»`;

  // Новый лимит длиннее: должен быть не меньше всех более коротких лимитов, которые он покрывает
  const shorter = others
    .filter((o) => periodDays(o) < periodDays(me) && covers(cats, o.categories) && amount < o.amount)
    .sort((x, y) => y.amount - x.amount)[0];
  if (shorter) return deny('HIERARCHY', `Сумма должна быть не меньше ${money(shorter.amount)} — это лимит ${name(shorter)} на более короткий срок`);

  // Новый лимит короче: не больше всех более длинных лимитов, которые покрывают его категории
  const longer = others
    .filter((o) => periodDays(o) > periodDays(me) && covers(o.categories, cats) && amount > o.amount)
    .sort((x, y) => x.amount - y.amount)[0];
  if (longer) return deny('HIERARCHY', `Сумма должна быть не больше ${money(longer.amount)} — это лимит ${name(longer)} на более длинный срок`);

  return { ok: true, value: { count, unit: draft.unit, amount, categories: cats } };
}

/* ───────────── Возраст ───────────── */

export const COHORTS = {
  junior: { label: '7–13 лет', brand: 'Moby-Young (7–13)', p2pAllowed: false },
  teen: { label: '14–17 лет', brand: 'Moby-Young (14–17)', p2pAllowed: true },
};

/* ───────────── Проверки операций ───────────── */

/**
 * Проверка покупки. Порядок проверок = порядок причин отказа.
 * @param {{amount: number, mcc: string, online?: boolean}} op
 */
export function checkPayment(child, op, now = Date.now()) {
  const g = groupOf(op.mcc);
  const hard = HARD_BLOCKS.find((h) => h.key === g);
  if (hard) return deny('HARD_BLOCK', `«${hard.label}» — запрещено для несовершеннолетних`);
  if (child.card.frozen) return deny('FROZEN', 'Счёт заблокирован');
  if (g in child.allow && !child.allow[g]) return deny('PARENT_CATEGORY', `Категория «${category(g).label}» запрещена`);
  if (op.online && !child.allow.online) return deny('PARENT_ONLINE', 'Покупки в интернете запрещены');
  if (op.amount > child.balance) return deny('FUNDS', 'Недостаточно средств');
  const tight = tightestLimit(child, op, now);
  if (tight && op.amount > tight.usage.remaining) {
    return deny('LIMIT', `Превышен лимит «${periodTitle(tight.limit, now)} · ${limitCategoriesLabel(tight.limit)}»: осталось ${money(tight.usage.remaining)}`);
  }
  return { ok: true };
}

/** Активное разовое одобрение перевода сверх лимита. */
export const activeApproval = (child) => (child.approvals ?? []).find((a) => a.status === 'active') ?? null;

/**
 * Котировка перевода для подростка: что произойдёт при нажатии кнопки.
 *  mode 'send'      — укладывается в лимиты;
 *  mode 'approved'  — сверх лимита, но в пределах одобрения родителя (одобрение будет израсходовано);
 *  mode 'request'   — сверх лимита: кнопка превращается в «Запросить у …»;
 *  mode 'blocked'   — перевод невозможен (reason).
 */
export function quoteP2P(child, amount, now = Date.now()) {
  if (child.card.frozen) return { mode: 'blocked', ...deny('FROZEN', 'Счёт заблокирован') };
  if (!COHORTS[child.cohort].p2pAllowed) return { mode: 'blocked', ...deny('AGE', 'Переводы доступны с 14 лет') };
  if (!child.allow.p2p) return { mode: 'blocked', ...deny('PARENT_P2P', 'Переводы запрещены') };
  if (!(amount > 0)) return { mode: 'blocked', ...deny('AMOUNT', 'Укажите сумму — не больше двух знаков после запятой') };

  const tight = tightestLimit(child, { mcc: '4829', amount }, now);
  const withinLimit = !tight || amount <= tight.usage.remaining;
  const approval = activeApproval(child);

  if (withinLimit) {
    return amount > child.balance ? { mode: 'blocked', ...deny('FUNDS', 'Недостаточно средств') } : { mode: 'send', ok: true, tight };
  }
  if (approval && amount <= approval.max) {
    return amount > child.balance ? { mode: 'blocked', ...deny('FUNDS', 'Недостаточно средств') } : { mode: 'approved', ok: true, tight, approval };
  }
  return { mode: 'request', ok: false, code: 'NEEDS_APPROVAL', tight, approval, reason: `Сумма выше лимита: доступно ${money(tight.usage.remaining)}` };
}

export function deny(code, reason) {
  return { ok: false, code, reason };
}

export const DAYS_SHORT = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'];
export const DAYS_ACC = ['понедельник', 'вторник', 'среду', 'четверг', 'пятницу', 'субботу', 'воскресенье'];

export const AUTOPAY_EVERY = {
  day: 'Ежедневно',
  week: 'Еженедельно',
  month: 'Ежемесячно',
};

/** Человеческое описание расписания автоперевода. */
export function autopaySchedule(a) {
  if (a.every === 'day') return 'каждый день';
  if (a.every === 'week') return `каждую неделю, в ${DAYS_ACC[a.weekday]}`;
  return `каждый месяц, ${a.monthDay}-го числа`;
}
