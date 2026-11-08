/**
 * Окно «Ребёнок 14–17» — Moby-Young (14–17), подростковый финтех-контур.
 * Без детских элементов. Переводы ограничены лимитами родителя; сумму сверх лимита
 * можно запросить у главного родственника как разовое одобрение.
 */
import { icon } from '../ui/icons.js';
import { card, dropdown, emptyState, moneyAttrs, progress, switchBtn, txList } from '../ui/components.js';
import { openScanner, openSheet, toast } from '../ui/overlay.js';
import { esc, int, money, pct, plural } from '../core/format.js';
import { groupLabel, groupOf, limitCategoriesLabel, parseMoney, periodTitle, quoteP2P, round2, spendable } from '../core/rules.js';
import { CONTACTS, QR_MERCHANTS } from '../core/catalog.js';
import { mascotActions, mascotCard } from './mascot.js';
import { childHeader, mainRelative, myLimitsCard, openAskMoney, piggyActions, piggyCard, requestStatus, submitPiggy } from './shared.js';

const CHILD_ID = 'ivan';
const DAY = 86_400_000;
const PERIODS = [
  { value: 'today', label: 'за сегодня' },
  { value: '7', label: 'за 7 дней' },
  { value: '30', label: 'за 30 суток' },
];

/** Локальное UI-состояние окна (в глобальный store не попадает). */
const local = { period: '7' };

export const teenView = {
  childId: CHILD_ID,

  render(state, now) {
    const c = state.children[CHILD_ID];
    const frozen = c.card.frozen;

    return `<div class="app">
      ${childHeader(c)}
      ${mascotCard(c, now)}

      <div class="section quick-actions" aria-label="Быстрые действия">
        <button class="quick-action quick-action--primary" data-action="teen/p2p" ${!c.allow.p2p || frozen ? 'disabled' : ''}>
          <span class="quick-action__icon">${icon('send')}</span>Перевести</button>
        <button class="quick-action" data-action="teen/ask" ${frozen ? 'disabled' : ''}><span class="quick-action__icon">${icon('arrowDown')}</span>Запросить</button>
        <button class="quick-action" data-action="teen/qr" ${frozen ? 'disabled' : ''}><span class="quick-action__icon">${icon('scan')}</span>QR КРОК</button>
        <button class="quick-action" data-action="teen/details"><span class="quick-action__icon">${icon('eye')}</span>Реквизиты</button>
      </div>
      ${!c.allow.p2p ? `<div class="section notice notice--red">${icon('lock')}<span>Переводы запрещены главным родственником</span></div>` : ''}
      ${requestStatus(state, CHILD_ID, now)}

      ${myLimitsCard(state, c, now)}
      ${piggyCard(state, c)}
      ${subscriptionsCard(c)}
      ${analyticsCard(c, now)}
      ${card({ fit: true, title: 'История', aside: `<span class="muted tiny">${historyCount(c)}</span>`, body: txList(c.tx, { now }) })}
    </div>`;
  },

  actions: {
    'teen/p2p': ({ store }) => openP2P(store),
    'teen/ask': ({ store }) => openAskMoney(store, CHILD_ID, { amount: '10', reason: 'На кружок' }),
    'teen/qr': ({ store }) => {
      openScanner({
        merchants: QR_MERCHANTS.teen,
        available: () => money(spendable(store.getState().children[CHILD_ID])),
        onPay: (m) => store.dispatch('pay/qr', { childId: CHILD_ID, merchantId: m.id }),
      });
    },
    'teen/details': ({ store }) => openDetails(store.getState().children[CHILD_ID]),
    'teen/subToggle': async ({ el, store }) => {
      const res = await store.dispatch('sub/toggle', { childId: CHILD_ID, id: el.dataset.id });
      if (!res.ok) toast(res.reason, 'error');
    },
    'teen/subEdit': ({ el, store }) => openSubEdit(store, el.dataset.id),
    'teen/subDelete': async ({ el, store }) => {
      const res = await store.dispatch('sub/delete', { childId: CHILD_ID, id: el.dataset.id });
      res.ok ? toast('Подписка удалена') : toast(res.reason, 'error');
    },
    'child/piggy': submitPiggy,
    ...piggyActions,
    ...mascotActions,
  },

  changes: {
    'teen/period': ({ value, rerender }) => {
      local.period = value;
      rerender();
    },
  },

};

/* ───────────── Блоки ───────────── */

function historyCount(c) {
  const n = c.stats.txCount + c.tx.length;
  return `${int(n)} ${plural(n, ['операция', 'операции', 'операций'])} с ${c.stats.since}`;
}

/** Реквизиты карты учащегося. */
function openDetails(c) {
  const row = (label, value) => `<div class="detail-row"><span>${label}</span><b class="num">${esc(value)}</b></div>`;
  openSheet({
    title: 'Реквизиты карты',
    body: `<div class="details">
      ${row('Номер карты', c.card.number)}
      ${row('Держатель', c.card.holder)}
      ${row('Действует до', c.card.validThru)}
      ${row('Student ID', c.card.studentId)}
      ${row('CVV', 'придёт в push')}
    </div>
    <p class="muted tiny">Никому не сообщай CVV и коды из СМС — даже если звонят «из банка».</p>`,
  });
}

function subscriptionsCard(c) {
  const total = round2(c.subscriptions.filter((s) => s.active).reduce((sum, s) => sum + s.price, 0));
  const frozen = c.card.frozen;
  const body = c.subscriptions.length
    ? `<ul>${c.subscriptions
        .map(
          (s) => `<li class="sub-row ${s.active ? '' : 'sub-row--off'}">
        <span class="sub-row__icon">${icon(s.icon ?? 'receipt')}</span>
        <div class="grow">
          <div class="sub-row__title">${esc(s.title)}</div>
          <div class="tiny muted">${money(s.price)}/мес · ${s.active ? `списание ${esc(s.next)}` : 'выключена'}</div>
          <div class="sub-row__actions">
            <button class="link-btn" data-action="teen/subEdit" data-id="${s.id}" ${frozen ? 'disabled' : ''}>Изменить сумму</button>
            <button class="link-btn link-btn--danger" data-action="teen/subDelete" data-id="${s.id}" ${frozen ? 'disabled' : ''}>Удалить</button>
          </div>
        </div>
        ${switchBtn({ on: s.active, action: 'teen/subToggle', data: { id: s.id }, label: s.title, disabled: frozen })}
      </li>`,
        )
        .join('')}</ul>`
    : emptyState('receipt', 'Подписок нет');
  return card({ fit: true, title: 'Подписки', aside: `<span class="muted small num">${money(total)}/мес</span>`, body });
}

function analyticsCard(c, now) {
  const from = local.period === 'today' ? new Date(now).setHours(0, 0, 0, 0) : now - Number(local.period) * DAY;
  const byCat = new Map();
  for (const t of c.tx) {
    if (t.status !== 'ok' || t.amount >= 0 || t.ts < from) continue;
    const g = groupOf(t.mcc);
    if (g === 'piggy') continue;
    const label = groupLabel(t.mcc);
    byCat.set(label, round2((byCat.get(label) ?? 0) - t.amount));
  }
  const rows = [...byCat.entries()].sort((a, b) => b[1] - a[1]);
  const max = rows[0]?.[1] ?? 1;
  const total = round2(rows.reduce((s, [, v]) => s + v, 0));
  return card({
    fit: true,
    title: 'Куда уходят деньги',
    aside: dropdown({ name: 'period', value: local.period, options: PERIODS, label: 'Период', change: 'teen/period' }),
    body: rows.length
      ? `<p class="small muted" style="margin-bottom:10px">Всего: <b style="color:var(--mt-ink)">${money(total)}</b></p>
         <div class="bars">${rows.map(([label, sum]) => `<div><div class="bar__head"><span>${esc(label)}</span><b>${money(sum)}</b></div>${progress(pct(sum, max))}</div>`).join('')}</div>`
      : emptyState('receipt', 'За этот период трат нет'),
  });
}

/* ───────────── Шиты ───────────── */

function openP2P(store) {
  const rel = mainRelative(store.getState());
  const child = () => store.getState().children[CHILD_ID];
  let contactId = CONTACTS[0].id;

  /** Пересчитывает подсказку по лимиту и текст кнопки под текущую сумму. */
  const sync = (api) => {
    const form = api.el.querySelector('form');
    const amount = parseMoney(form.amount.value);
    const q = quoteP2P(child(), amount);
    const btn = form.querySelector('[type=submit]');
    btn.dataset.mode = q.mode;
    btn.innerHTML = q.mode === 'request' ? `${icon('hand')} Запросить у ${esc(rel.genitive)}` : `${icon('send')} Перевести`;
    const info = api.el.querySelector('[data-limit-info]');
    info.innerHTML = limitInfo(child(), q);
    api.error('');
  };

  openSheet({
    title: 'Перевод другу',
    body: `<form class="stack" style="gap:14px" novalidate>
      <div class="field"><span class="field__label">Кому</span>
        <div class="contact-list">${CONTACTS.map(
          (p, i) => `<button type="button" class="contact" aria-pressed="${i === 0}" data-sheet-action="contact" data-id="${p.id}">
            <span class="avatar avatar--sm">${p.initials}</span><span><span class="contact__name">${esc(p.name)}</span><span class="tiny muted">${p.phone}</span></span></button>`,
        ).join('')}</div></div>
      <label class="field"><span class="field__label">Сумма, BYN</span>
        <input class="input input--amount" name="amount" ${moneyAttrs} value="10" /></label>
      <div class="chips">${['5', '10', '35'].map((v) => `<button type="button" class="chip" data-sheet-action="amount" data-value="${v}">${v} BYN</button>`).join('')}</div>
      <div data-limit-info></div>
      <p class="field__error" data-error></p>
      <button class="btn btn--ray btn--lg btn--block" type="submit"></button></form>`,
    onMount: (el) => sync({ el, error: () => {} }),
    async onAction(name, el, api) {
      const form = api.el.querySelector('form');
      if (name === 'contact') {
        contactId = el.dataset.id;
        api.el.querySelectorAll('.contact').forEach((b) => b.setAttribute('aria-pressed', String(b === el)));
        return;
      }
      if (name === 'amount') {
        form.amount.value = el.dataset.value;
        return sync(api);
      }
      if (name === 'input' && el.name === 'amount') return sync(api);
      if (name !== 'submit') return;

      const mode = form.querySelector('[type=submit]').dataset.mode;
      if (mode === 'request') {
        const res = await store.dispatch('p2p/requestOver', { childId: CHILD_ID, contactId, amount: form.amount.value });
        if (!res.ok) return api.error(res.reason);
        api.close();
        return toast(`Запрос отправлен ${rel.dative}`);
      }
      const res = await store.dispatch('p2p/send', { childId: CHILD_ID, contactId, amount: form.amount.value });
      if (!res.ok) return api.error(res.reason);
      api.close();
      toast(res.mode === 'approved' ? 'Перевод выполнен · разовое одобрение использовано' : 'Перевод выполнен');
    },
  });
}

/** Подсказка о лимите на переводы под полем суммы. */
function limitInfo(c, q) {
  const parts = [];
  if (q.tight) {
    const { limit, usage } = q.tight;
    parts.push(`<div class="approval approval--pending">${icon('target')}<span>Лимит «${esc(periodTitle(limit))} · ${esc(limitCategoriesLabel(limit))}»: доступно <b>${money(usage.remaining)}</b> из ${money(limit.amount)}</span></div>`);
  }
  const approval = (c.approvals ?? []).find((a) => a.status === 'active');
  if (approval) {
    parts.push(
      `<div class="approval">${icon('shieldCheck')}<span>${q.mode === 'approved' ? 'Будет использовано' : 'Есть'} разовое одобрение сверх лимита до <b>${money(approval.max)}</b></span></div>`,
    );
  }
  return `<div class="stack">${parts.join('')}</div>`;
}

function openSubEdit(store, id) {
  const s = store.getState().children[CHILD_ID].subscriptions.find((x) => x.id === id);
  if (!s) return;
  openSheet({
    title: s.title,
    body: `<form class="stack" style="gap:14px" novalidate>
      <label class="field"><span class="field__label">Сумма в месяц, BYN</span>
        <input class="input input--amount" name="price" ${moneyAttrs} value="${String(s.price).replace('.', ',')}" /></label>
      <p class="field__error" data-error></p>
      <div class="sheet__actions">
        <button class="btn btn--ghost btn--lg" type="button" data-sheet-action="close">Отменить</button>
        <button class="btn btn--primary btn--lg" type="submit">Сохранить</button>
      </div></form>`,
    async onAction(name, el, api) {
      if (name !== 'submit') return;
      const res = await store.dispatch('sub/update', { childId: CHILD_ID, id, price: el.price.value });
      if (!res.ok) return api.error(res.reason);
      api.close();
      toast('Сумма подписки изменена');
    },
  });
}
