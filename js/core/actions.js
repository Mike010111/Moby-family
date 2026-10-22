/**
 * Редьюсеры — все допустимые изменения состояния.
 * Каждый получает черновик (можно мутировать) и возвращает результат для UI.
 * Именование: "<домен>/<действие>".
 */
import {
  CATEGORIES,
  activeApproval,
  categoriesFor,
  checkPayment,
  groupLabel,
  groupOf,
  parseMoney,
  periodLabel,
  unitStart,
  quoteP2P,
  round2,
  validateLimit,
} from './rules.js';
import { CONTACTS, GEO_PLACES, LESSONS, QR_MERCHANTS } from './catalog.js';
import { ARTIFACTS, RARITY, SHOP, cashbackAmount, cashbackFor, categoryLabel, cycleWeeks, isActive, makeOwned, offerState, renewOffer } from './artifacts.js';
import { money } from './format.js';

const FROZEN = { ok: false, code: 'FROZEN', reason: 'Счёт заблокирован' };

function child(draft, id) {
  const c = draft.children[id];
  if (!c) throw new Error(`[actions] Unknown child: ${id}`);
  return c;
}

const mainRelative = (draft) => draft.relatives.find((r) => r.main);

/** Глагол прошедшего времени в роде родственника: «одобрила» / «одобрил». */
const verb = (rel, stem) => `${stem}${rel.female ? 'а' : ''}`;

function pushTx(c, ctx, tx) {
  c.tx.unshift({ id: ctx.uid(), ts: ctx.now, status: 'ok', ...tx });
  c.tx.length = Math.min(c.tx.length, 60);
}

function pushFeed(draft, ctx, item) {
  draft.feed.unshift({ id: ctx.uid(), ts: ctx.now, ...item });
  draft.feed.length = Math.min(draft.feed.length, 40);
}

/** Уведомление ребёнку — покажется тостом, когда откроют его экран. */
function notify(c, ctx, text, tone = 'ok') {
  c.inbox.unshift({ id: ctx.uid(), text, tone, ts: ctx.now, seen: false });
  c.inbox.length = Math.min(c.inbox.length, 20);
}

/** Перевод главный родственник → ребёнок. */
function transferFromParent(draft, ctx, c, amount, title) {
  if (c.card.frozen) return FROZEN;
  if (amount > draft.parent.balance) return { ok: false, reason: 'Недостаточно средств на счёте' };
  draft.parent.balance = round2(draft.parent.balance - amount);
  c.balance = round2(c.balance + amount);
  pushTx(c, ctx, { title, amount, mcc: 'topup' });
  return { ok: true };
}

const PIGGY_MAX = 5;

/** 7–13: менять и удалять копилки можно только с одобрения главного родственника. */
const piggyNeedsApproval = (c) => c.cohort === 'junior';

/** Деньги из копилки → на счёт ребёнка, копилка закрывается. */
function removePiggy(ctx, c, p, title = `Возврат из копилки «${p.goal}»`) {
  const amount = p.saved;
  if (amount > 0) {
    c.balance = round2(c.balance + amount);
    pushTx(c, ctx, { title, amount, mcc: 'piggy' });
  }
  c.piggies = c.piggies.filter((x) => x.id !== p.id);
  return amount;
}

/** Цель достигнута: деньги на счёт, сообщение ребёнку и событие главному родственнику. */
function completePiggy(d, ctx, c, p) {
  const amount = removePiggy(ctx, c, p, `Копилка «${p.goal}» собрана`);
  notify(c, ctx, `Копилка «${p.goal}» собрана! ${money(amount)} зачислены на счёт`);
  pushFeed(d, ctx, { kind: 'info', childId: c.id, title: `${c.name}: копилка «${p.goal}» собрана`, text: `${money(amount)} зачислены на счёт ребёнка` });
  return { goal: p.goal, amount };
}

/** Запрос главному родственнику на изменение / удаление копилки. */
function requestPiggyChange(d, ctx, c, p, kind, amount) {
  if (d.requests.some((r) => r.piggyId === p.id && r.status === 'pending')) {
    return { ok: false, code: 'PENDING', reason: 'По этой копилке уже ждём ответа' };
  }
  const rel = mainRelative(d);
  const id = ctx.uid();
  const reason = kind === 'piggy-delete' ? `Удалить копилку «${p.goal}»` : `Изменить цель «${p.goal}»: ${money(p.target)} → ${money(amount)}`;
  d.requests.unshift({ id, kind, childId: c.id, relativeId: rel.id, piggyId: p.id, amount, reason, status: 'pending', ts: ctx.now });
  const text = kind === 'piggy-delete' ? `Накоплено ${money(p.saved)} — вернётся на счёт ребёнка` : `Накоплено ${money(p.saved)} из ${money(p.target)}`;
  pushFeed(d, ctx, { kind: 'request', childId: c.id, requestId: id, title: `${c.name}: ${reason[0].toLowerCase()}${reason.slice(1)}`, text });
  return { ok: true, pending: true, relative: rel, logNote: `${c.name} → ${rel.label}: ${reason}` };
}

const badMoney = { ok: false, code: 'AMOUNT', reason: 'Укажите сумму — не больше двух знаков после запятой' };

export const reducers = {
  /* ───────────── UI (в журнал не пишутся) ───────────── */
  'ui/setRole': (d, { role }) => {
    if (d.ui.role === role) return { noop: true };
    d.ui.role = role;
  },
  'ui/selectChild': (d, { childId }) => {
    if (d.ui.selectedChildId === childId || !d.children[childId]) return { noop: true };
    d.ui.selectedChildId = childId;
  },
  'ui/markSeen': (d, { childId }) => {
    const c = child(d, childId);
    if (!c.inbox.some((n) => !n.seen)) return { noop: true };
    c.inbox.forEach((n) => (n.seen = true));
  },

  /* ───────────── Запросы ребёнка → родственнику ───────────── */
  'request/create': (d, { childId, relativeId, amount, reason }, ctx) => {
    const c = child(d, childId);
    if (c.card.frozen) return FROZEN;
    const value = parseMoney(amount);
    if (!(value > 0)) return badMoney;
    if (value > 500) return { ok: false, reason: 'Слишком большая сумма для запроса' };
    const rel = d.relatives.find((r) => r.id === relativeId) ?? mainRelative(d);
    const text = String(reason || 'Без причины').trim().slice(0, 60) || 'Без причины';
    const id = ctx.uid();

    // Окно в демо есть только у главного родственника — остальным запрос просто «уходит».
    const status = rel.main ? 'pending' : 'sent';
    d.requests.unshift({ id, kind: 'topup', childId, relativeId: rel.id, amount: value, reason: text, status, ts: ctx.now });
    if (rel.main) pushFeed(d, ctx, { kind: 'request', childId, requestId: id, title: `${c.name} просит ${money(value)}`, text });
    return { ok: true, id, relative: rel, logNote: `${c.name} → ${rel.label}: ${money(value)} · ${text}` };
  },

  'request/approve': (d, { id }, ctx) => {
    const r = d.requests.find((x) => x.id === id);
    if (!r || r.status !== 'pending') return { ok: false, reason: 'Запрос уже обработан' };
    const c = child(d, r.childId);
    if (c.card.frozen) return { ok: false, code: 'FROZEN', reason: `Счёт ${c.name} заблокирован — сначала разблокируйте` };
    const rel = mainRelative(d);

    if (r.kind === 'topup') {
      const res = transferFromParent(d, ctx, c, r.amount, `${rel.label} ${verb(rel, 'одобрил')}: ${r.reason}`);
      if (!res.ok) return res;
      notify(c, ctx, `${rel.label} ${verb(rel, 'одобрил')} запрос: +${money(r.amount)}`);
    } else if (r.kind === 'p2p-over') {
      for (const a of c.approvals) if (a.status === 'active') a.status = 'replaced';
      c.approvals.unshift({ id: ctx.uid(), requestId: r.id, max: r.amount, status: 'active', ts: ctx.now });
      notify(c, ctx, `${rel.label} ${verb(rel, 'одобрил')} разовый перевод сверх лимита до ${money(r.amount)}`);
    } else if (r.kind === 'piggy-update' || r.kind === 'piggy-delete') {
      const p = c.piggies.find((x) => x.id === r.piggyId);
      if (!p) {
        r.status = 'cancelled';
        r.resolution = 'Копилки уже нет';
        return { ok: false, reason: 'Копилки уже нет — запрос отменён' };
      }
      if (r.kind === 'piggy-delete') {
        const returned = removePiggy(ctx, c, p);
        notify(c, ctx, `${rel.label} ${verb(rel, 'разрешил')} удалить копилку «${p.goal}»: ${money(returned)} вернулись на счёт`);
      } else {
        if (r.amount < p.saved) return { ok: false, reason: `Уже собрано ${money(p.saved)} — это больше новой цели` };
        p.target = r.amount;
        notify(c, ctx, `${rel.label} ${verb(rel, 'одобрил')} новую цель «${p.goal}»: ${money(r.amount)}`);
        if (p.saved >= p.target) completePiggy(d, ctx, c, p);
      }
    }

    r.status = 'approved';
    r.resolvedAt = ctx.now;
    return { ok: true, logNote: `${c.name}: одобрено · ${r.reason}` };
  },

  'request/decline': (d, { id }, ctx) => {
    const r = d.requests.find((x) => x.id === id);
    if (!r || r.status !== 'pending') return { ok: false, reason: 'Запрос уже обработан' };
    const c = child(d, r.childId);
    r.status = 'declined';
    r.resolvedAt = ctx.now;
    const what =
      r.kind === 'p2p-over' ? `перевод ${money(r.amount)} сверх лимита` : r.kind.startsWith('piggy') ? r.reason.toLowerCase() : `${money(r.amount)} · ${r.reason}`;
    const rel = mainRelative(d);
    notify(c, ctx, `${rel.label} ${verb(rel, 'отклонил')} запрос: ${what}`, 'error');
    return { ok: true, logNote: `${c.name}: отклонено` };
  },

  /* ───────────── Лимиты ───────────── */
  'limit/create': (d, { childId, ...draft }, ctx) => {
    const c = child(d, childId);
    const v = validateLimit(c, draft, undefined, ctx.now);
    if (!v.ok) return v;
    // Периоды календарные: отсчёт с начала текущего дня / недели / месяца
    c.limits.push({ id: ctx.uid(), ...v.value, start: unitStart(ctx.now, v.value.unit) });
    return { ok: true, logNote: `${c.name}: лимит ${money(v.value.amount)} / ${periodLabel(v.value)}` };
  },

  'limit/update': (d, { childId, id, ...draft }, ctx) => {
    const c = child(d, childId);
    const limit = c.limits.find((l) => l.id === id);
    if (!limit) return { ok: false, reason: 'Лимит не найден' };
    const v = validateLimit(c, draft, id, ctx.now);
    if (!v.ok) return v;
    const periodChanged = v.value.count !== limit.count || v.value.unit !== limit.unit;
    Object.assign(limit, v.value);
    if (periodChanged || limit.start == null) limit.start = unitStart(ctx.now, limit.unit);
    return { ok: true, logNote: `${c.name}: лимит изменён → ${money(v.value.amount)} / ${periodLabel(v.value)}` };
  },

  'limit/delete': (d, { childId, id }) => {
    const c = child(d, childId);
    const before = c.limits.length;
    c.limits = c.limits.filter((l) => l.id !== id);
    if (c.limits.length === before) return { ok: false, reason: 'Лимит не найден' };
    return { ok: true, logNote: `${c.name}: лимит удалён` };
  },

  /* ───────────── Родительский контроль ───────────── */
  'parent/toggleAllow': (d, { childId, key }) => {
    const c = child(d, childId);
    if (!categoriesFor(c).some((cat) => cat.key === key)) {
      return { ok: false, reason: key === 'p2p' ? 'Переводы доступны с 14 лет' : 'Настройка недоступна' };
    }
    c.allow[key] = !c.allow[key];
    const label = CATEGORIES.find((cat) => cat.key === key).label;
    return { ok: true, logNote: `${c.name}: «${label}» → ${c.allow[key] ? 'разрешено' : 'запрещено'}` };
  },

  'parent/toggleFreeze': (d, { childId }, ctx) => {
    const c = child(d, childId);
    c.card.frozen = !c.card.frozen;
    pushFeed(d, ctx, {
      kind: c.card.frozen ? 'alert' : 'info',
      childId,
      title: c.card.frozen ? `Счёт ${c.name} заблокирован` : `Счёт ${c.name} разблокирован`,
      text: `${c.card.title} ${c.card.mask}`,
    });
    const rel = mainRelative(d);
    notify(c, ctx, `${rel.label} ${verb(rel, c.card.frozen ? 'заблокировал' : 'разблокировал')} счёт`, c.card.frozen ? 'error' : 'ok');
    return { ok: true, logNote: `${c.name}: счёт ${c.card.frozen ? 'заблокирован' : 'активен'}` };
  },

  'parent/topUp': (d, { childId, amount }, ctx) => {
    const c = child(d, childId);
    const value = parseMoney(amount);
    if (!(value > 0)) return badMoney;
    const res = transferFromParent(d, ctx, c, value, `Пополнение от ${mainRelative(d).genitive}`);
    return res.ok ? { ok: true, logNote: `${c.name}: +${money(value)}` } : res;
  },

  'autopay/update': (d, { childId, enabled, amount, every, weekday, monthDay }) => {
    const c = child(d, childId);
    const value = parseMoney(amount);
    if (!(value > 0)) return badMoney;
    if (!['day', 'week', 'month'].includes(every)) return { ok: false, reason: 'Выберите периодичность' };
    const wd = Number(weekday);
    const md = Number(monthDay);
    if (every === 'week' && !(Number.isInteger(wd) && wd >= 0 && wd <= 6)) return { ok: false, reason: 'Выберите день недели' };
    if (every === 'month' && !(Number.isInteger(md) && md >= 1 && md <= 28)) return { ok: false, reason: 'Число месяца — от 1 до 28' };
    c.autopay = {
      enabled: Boolean(enabled),
      amount: value,
      every,
      weekday: every === 'week' ? wd : c.autopay.weekday,
      monthDay: every === 'month' ? md : c.autopay.monthDay,
    };
    return { ok: true, logNote: `${c.name}: автоперевод ${money(value)} · ${enabled ? 'вкл' : 'выкл'}` };
  },

  'parent/refreshGeo': (d, { childId }, ctx) => {
    const c = child(d, childId);
    const options = GEO_PLACES.filter((p) => p.name !== c.geo.place);
    const next = options[Math.floor(Math.random() * options.length)];
    c.geo = { ...next, place: next.name, updated: ctx.now };
    c.battery = Math.max(5, c.battery - 1 - Math.floor(Math.random() * 4));
    return { ok: true, logNote: `${c.name}: ${next.name}, ${c.battery}%` };
  },

  'parent/addTask': (d, { childId, title, reward }, ctx) => {
    const c = child(d, childId);
    if (c.card.frozen) return FROZEN;
    const text = String(title || '').trim().slice(0, 50);
    if (!text) return { ok: false, reason: 'Опишите задание' };
    const value = String(reward).trim() === '0' ? 0 : parseMoney(reward);
    if (!(value >= 0)) return badMoney;
    c.tasks.unshift({ id: ctx.uid(), title: text, reward: value, status: 'open' });
    return { ok: true, logNote: `${c.name}: «${text}» за ${money(value)}` };
  },

  /* ───────────── Задания и обучение ───────────── */
  'task/complete': (d, { childId, taskId }, ctx) => {
    const c = child(d, childId);
    if (c.card.frozen) return FROZEN;
    const t = c.tasks.find((x) => x.id === taskId);
    if (!t || t.status !== 'open') return { ok: false, reason: 'Задание уже отправлено' };
    t.status = 'review';
    pushFeed(d, ctx, { kind: 'task', childId, taskId, title: `${c.name}: задание выполнено`, text: `${t.title} · награда ${money(t.reward)}` });
    return { ok: true, logNote: `${c.name}: «${t.title}» на проверке` };
  },

  'task/approve': (d, { childId, taskId }, ctx) => {
    const c = child(d, childId);
    if (c.card.frozen) return FROZEN;
    const t = c.tasks.find((x) => x.id === taskId);
    if (!t || t.status !== 'review') return { ok: false, reason: 'Задание уже проверено' };
    if (t.reward > 0) {
      const res = transferFromParent(d, ctx, c, t.reward, `Награда: ${t.title}`);
      if (!res.ok) return res;
    }
    t.status = 'done';
    c.points += 5;
    notify(c, ctx, `Задание «${t.title}» принято: +${money(t.reward)} и 5 баллов`);
    return { ok: true, logNote: `${c.name}: награда ${money(t.reward)} +5 баллов` };
  },

  /** Уроки работают и при заблокированном счёте: баллы не блокируются. */
  'lesson/complete': (d, { childId }) => {
    const c = child(d, childId);
    if (c.lessons.done >= LESSONS.length) return { ok: false, reason: 'Все уроки пройдены' };
    c.lessons.done += 1;
    c.points += 10;
    return { ok: true, logNote: `${c.name}: урок ${c.lessons.done}/${LESSONS.length}, +10 баллов` };
  },

  /* ───────────── Платежи ───────────── */
  'pay/qr': (d, { childId, merchantId }, ctx) => {
    const c = child(d, childId);
    const list = QR_MERCHANTS[c.cohort];
    const m = list.find((x) => x.id === merchantId);
    if (!m) return { ok: false, reason: 'QR-код не распознан' };

    const check = checkPayment(c, { amount: m.amount, mcc: m.mcc }, ctx.now);
    if (!check.ok) {
      pushTx(c, ctx, { title: m.name, amount: -m.amount, mcc: m.mcc, status: 'declined', note: check.reason });
      pushFeed(d, ctx, { kind: 'alert', childId, title: `Отклонена оплата: ${m.name}`, text: `${money(m.amount)} · ${check.reason}` });
      return { ...check, merchant: m };
    }

    c.balance = round2(c.balance - m.amount);
    const earned = Math.max(1, Math.round(m.amount));
    c.points += earned; // баллы ребёнка, не родителя
    const legal = c.cohort === 'junior' ? 'Мелкая бытовая сделка · ст. 27 ГК РБ' : 'QR КРОК';
    pushTx(c, ctx, { title: m.name, amount: -m.amount, mcc: m.mcc, note: legal });

    // Кэшбэк от артефакта маскота на категорию покупки
    let cashback = null;
    const art = cashbackFor(c, groupOf(m.mcc), ctx.now);
    if (art) {
      const value = cashbackAmount(m.amount, art.cashback);
      c.balance = round2(c.balance + value);
      cashback = { value, pct: art.cashback, artifact: art.name };
      pushTx(c, ctx, { title: `Кэшбэк ${art.cashback}% · ${art.name}`, amount: value, mcc: 'cashback', note: groupLabel(m.mcc), ts: ctx.now + 1 });
    }
    pushFeed(d, ctx, { kind: 'info', childId, title: `${c.name} ${c.female ? 'оплатила' : 'оплатил'} «${m.name}»`, text: `${money(m.amount)} · ${groupLabel(m.mcc)}` });
    return { ok: true, merchant: m, earned, legal, cashback, logNote: `${c.name}: −${money(m.amount)} ${m.name}` };
  },

  /* ───────────── Копилки (несколько целей) ───────────── */
  'piggy/create': (d, { childId, goal, target }, ctx) => {
    const c = child(d, childId);
    if (c.card.frozen) return FROZEN;
    const name = String(goal ?? '').trim().slice(0, 30);
    if (!name) return { ok: false, reason: 'Напиши, на что копишь' };
    const value = parseMoney(target);
    if (!(value > 0)) return badMoney;
    if (c.piggies.length >= PIGGY_MAX) return { ok: false, reason: `Можно вести не больше ${PIGGY_MAX} копилок` };
    if (c.piggies.some((p) => p.goal.toLowerCase() === name.toLowerCase())) return { ok: false, reason: 'Копилка с таким названием уже есть' };
    const id = ctx.uid();
    c.piggies.push({ id, goal: name, target: value, saved: 0 });
    return { ok: true, id, logNote: `${c.name}: новая копилка «${name}» на ${money(value)}` };
  },

  'piggy/deposit': (d, { childId, piggyId, amount }, ctx) => {
    const c = child(d, childId);
    if (c.card.frozen) return FROZEN;
    const p = c.piggies.find((x) => x.id === piggyId);
    if (!p) return { ok: false, reason: 'Выбери копилку' };
    const value = parseMoney(amount);
    if (!(value > 0)) return badMoney;
    if (value > c.balance) return { ok: false, reason: 'Не хватает денег на карте' };
    const room = round2(p.target - p.saved);
    if (value > room) return { ok: false, reason: `До цели осталось ${money(room)} — положи не больше` };
    c.balance = round2(c.balance - value);
    p.saved = round2(p.saved + value);
    c.points += 2;
    pushTx(c, ctx, { title: `В копилку «${p.goal}»`, amount: -value, mcc: 'piggy' });
    const done = p.saved >= p.target ? completePiggy(d, ctx, c, p) : null;
    return { ok: true, completed: done, logNote: `${c.name}: копилка «${p.goal}» ${money(p.saved)}${done ? ' — собрана' : ''}` };
  },

  /**
   * Изменение суммы цели. Меньше уже собранного нельзя; равно — копилка собрана.
   * 7–13: только через одобрение главного родственника (создаётся запрос).
   * 14–17: сразу (интерфейс показывает предупреждение).
   */
  'piggy/update': (d, { childId, piggyId, target }, ctx) => {
    const c = child(d, childId);
    if (c.card.frozen) return FROZEN;
    const p = c.piggies.find((x) => x.id === piggyId);
    if (!p) return { ok: false, reason: 'Копилка не найдена' };
    const value = parseMoney(target);
    if (!(value > 0)) return badMoney;
    if (value < p.saved) return { ok: false, code: 'BELOW_SAVED', reason: `Нельзя меньше уже собранного: ${money(p.saved)}` };
    if (value === p.target) return { ok: false, reason: 'Сумма не изменилась' };
    if (piggyNeedsApproval(c)) return requestPiggyChange(d, ctx, c, p, 'piggy-update', value);
    p.target = value;
    const done = p.saved >= p.target ? completePiggy(d, ctx, c, p) : null;
    return { ok: true, completed: done, logNote: `${c.name}: цель «${p.goal}» → ${money(value)}` };
  },

  /** Удаление копилки: накопленное возвращается на счёт. 7–13 — через одобрение. */
  'piggy/delete': (d, { childId, piggyId }, ctx) => {
    const c = child(d, childId);
    if (c.card.frozen) return FROZEN;
    const p = c.piggies.find((x) => x.id === piggyId);
    if (!p) return { ok: false, reason: 'Копилка не найдена' };
    if (piggyNeedsApproval(c)) return requestPiggyChange(d, ctx, c, p, 'piggy-delete', p.saved);
    const returned = removePiggy(ctx, c, p);
    return { ok: true, returned, logNote: `${c.name}: копилка «${p.goal}» удалена, вернулось ${money(returned)}` };
  },

  'p2p/send': (d, { childId, contactId, amount }, ctx) => {
    const c = child(d, childId);
    const contact = CONTACTS.find((x) => x.id === contactId);
    if (!contact) return { ok: false, reason: 'Выберите получателя' };
    const value = parseMoney(amount);
    const q = quoteP2P(c, value, ctx.now);
    if (q.mode === 'blocked' || q.mode === 'request') return { ok: false, code: q.code, reason: q.reason, mode: q.mode };

    c.balance = round2(c.balance - value);
    let note;
    if (q.mode === 'approved') {
      const a = activeApproval(c);
      a.status = 'used';
      a.usedAmount = value;
      note = `Сверх лимита · одобрено ${mainRelative(d).dative}`;
    }
    pushTx(c, ctx, { title: `Перевод: ${contact.name}`, amount: -value, mcc: '4829', note });
    return { ok: true, mode: q.mode, logNote: `${c.name}: P2P −${money(value)} ${contact.name}${note ? ' (одобрение)' : ''}` };
  },

  /** Перевод сверх лимита → запрос разового одобрения главному родственнику. */
  'p2p/requestOver': (d, { childId, contactId, amount }, ctx) => {
    const c = child(d, childId);
    const contact = CONTACTS.find((x) => x.id === contactId);
    if (!contact) return { ok: false, reason: 'Выберите получателя' };
    const value = parseMoney(amount);
    const q = quoteP2P(c, value, ctx.now);
    if (q.mode === 'blocked') return { ok: false, code: q.code, reason: q.reason };
    if (q.mode !== 'request') return { ok: false, reason: 'Сумма укладывается в лимит — запрос не нужен' };
    if (value > c.balance) return { ok: false, code: 'FUNDS', reason: 'Недостаточно средств' };

    for (const r of d.requests) {
      if (r.childId === childId && r.kind === 'p2p-over' && r.status === 'pending') {
        r.status = 'cancelled';
        r.resolution = 'Заменён новым запросом';
      }
    }
    const rel = mainRelative(d);
    const id = ctx.uid();
    d.requests.unshift({ id, kind: 'p2p-over', childId, relativeId: rel.id, amount: value, contactName: contact.name, reason: `Перевод: ${contact.name}`, status: 'pending', ts: ctx.now });
    pushFeed(d, ctx, {
      kind: 'request',
      childId,
      requestId: id,
      title: `${c.name} просит разрешить перевод ${money(value)} сверх лимита`,
      text: `Получатель: ${contact.name} · по лимиту доступно ${money(q.tight.usage.remaining)}`,
    });
    return { ok: true, relative: rel, logNote: `${c.name}: запрос перевода ${money(value)} сверх лимита` };
  },

  /* ───────────── Артефакты маскота (оплата баллами ребёнка) ───────────── */

  /**
   * Покупка артефакта недели. Баллы не блокируются вместе со счётом, поэтому покупка
   * доступна и при заблокированном счёте. Новый артефакт заменяет прежний того же слота;
   * купленный на этой неделе становится недоступен до её конца.
   */
  'artifact/buy': (d, { childId, id }, ctx) => {
    const c = child(d, childId);
    const art = c.artifacts;
    const a = ARTIFACTS[id];
    if (!a || a.slot !== art.weekSlot || !(SHOP[art.weekSlot] ?? []).includes(id)) return { ok: false, reason: 'Этот артефакт сейчас не продаётся' };
    const offer = offerState(c, id, ctx.now);
    if (offer.status === 'equipped') return { ok: false, code: 'EQUIPPED', reason: 'Этот артефакт уже у маскота' };
    if (offer.status === 'soldout') return { ok: false, code: 'SOLDOUT', reason: 'Уже покупали на этой неделе — снова будет в следующем цикле' };
    if (offer.status === 'nopoints') return { ok: false, code: 'POINTS', reason: `Не хватает ${offer.lack} баллов` };

    const prev = art.owned[a.slot];
    c.points -= offer.price;
    art.owned[a.slot] = makeOwned(id, c.cohort, { boughtAt: ctx.now, activeUntil: art.weekStart + cycleWeeks(c.cohort) * 7 * 86_400_000 });
    if (!art.soldOut.includes(id)) art.soldOut.push(id);
    const own = art.owned[a.slot];
    return {
      ok: true,
      artifact: own,
      replaced: isActive(prev, ctx.now) ? prev.name : null,
      logNote: `${c.name}: «${a.name}» за ${offer.price} баллов · ${own.cashback}% ${categoryLabel(own.category)}`,
    };
  },

  /** Продление своего артефакта в неделю его слота: кэшбэк −1 % (минимум 3 %). */
  'artifact/renew': (d, { childId }, ctx) => {
    const c = child(d, childId);
    const offer = renewOffer(c, ctx.now);
    if (!offer) return { ok: false, reason: 'Продлевать нечего' };
    if (c.points < offer.price) return { ok: false, code: 'POINTS', reason: `Не хватает ${offer.price - c.points} баллов` };
    const art = c.artifacts;
    c.points -= offer.price;
    Object.assign(art.owned[art.weekSlot], {
      cashback: offer.cashback,
      cycles: offer.artifact.cycles + 1,
      activeUntil: art.weekStart + cycleWeeks(c.cohort) * 7 * 86_400_000,
    });
    return { ok: true, logNote: `${c.name}: продлён «${offer.artifact.name}» · ${offer.cashback}% (${RARITY[offer.rarity].label})` };
  },

  /* ───────────── Подписки ───────────── */
  'sub/toggle': (d, { childId, id }) => {
    const c = child(d, childId);
    if (c.card.frozen) return FROZEN;
    const s = c.subscriptions.find((x) => x.id === id);
    if (!s) return { ok: false, reason: 'Подписка не найдена' };
    s.active = !s.active;
    return { ok: true, logNote: `${c.name}: «${s.title}» ${s.active ? 'включена' : 'выключена'}` };
  },

  'sub/update': (d, { childId, id, price }) => {
    const c = child(d, childId);
    if (c.card.frozen) return FROZEN;
    const s = c.subscriptions.find((x) => x.id === id);
    if (!s) return { ok: false, reason: 'Подписка не найдена' };
    const value = parseMoney(price);
    if (!(value > 0)) return badMoney;
    s.price = value;
    return { ok: true, logNote: `${c.name}: «${s.title}» → ${money(value)}/мес` };
  },

  'sub/delete': (d, { childId, id }) => {
    const c = child(d, childId);
    if (c.card.frozen) return FROZEN;
    const s = c.subscriptions.find((x) => x.id === id);
    if (!s) return { ok: false, reason: 'Подписка не найдена' };
    c.subscriptions = c.subscriptions.filter((x) => x.id !== id);
    return { ok: true, logNote: `${c.name}: «${s.title}» удалена` };
  },
};
