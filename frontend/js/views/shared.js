/**
 * Общие блоки детских окон Moby-Young (7–13 и 14–17):
 * шапка, «Мои лимиты», копилка, запрос денег у родственника, статус запроса.
 */
import { bankIcon, icon } from '../ui/icons.js';
import { card, dropdown, emptyState, limitList, moneyAttrs, ring } from '../ui/components.js';
import { openSheet, toast } from '../ui/overlay.js';
import { ago, esc, int, money, pct } from '../core/format.js';
import { COHORTS, activeApproval } from '../core/rules.js';
import { REQUEST_REASONS } from '../core/catalog.js';

export const mainRelative = (state) => state.relatives.find((r) => r.main);

/** Шапка: иконка банка, «Moby-Young (…)» над именем, баллы справа. */
export function childHeader(c) {
  return `<header class="app-header">
    ${bankIcon()}
    <div class="app-header__titles"><div class="app-header__eyebrow">${COHORTS[c.cohort].brand}</div>
      <h1 class="app-header__title">${esc(c.name)}</h1></div>
    <span class="points-pill" aria-label="Мои баллы: ${c.points}">${icon('star')} ${int(c.points)}</span>
  </header>`;
}

/** Лимиты ребёнка (только просмотр) + разовые одобрения переводов сверх лимита. */
export function myLimitsCard(state, c, now) {
  const rel = mainRelative(state);
  const approval = activeApproval(c);
  const pendingOver = state.requests.find((r) => r.childId === c.id && r.kind === 'p2p-over' && r.status === 'pending');
  const extra = [
    approval
      ? `<div class="approval">${icon('shieldCheck')}<span>Одобрен разовый перевод сверх лимитной суммы до <b>${money(approval.max)}</b></span></div>`
      : '',
    pendingOver
      ? `<div class="approval approval--pending">${icon('clock')}<span>Запрос ${esc(rel.dative)}: перевод <b>${money(pendingOver.amount)}</b> сверх лимита ждёт ответа</span></div>`
      : '',
  ].join('');
  return card({
    fit: true,
    title: 'Мои лимиты',
    aside: `<span class="badge">${c.limits.length}</span>`,
    body: `<div class="stack">${extra}${limitList(c, { now })}</div>`,
  });
}

/* ───────────── Копилки ───────────── */

/** Выбранная копилка для пополнения — локально для окна, в store не хранится. */
const selectedPiggy = {};
const PIGGY_IDEAS = ['Велосипед', 'Наушники', 'Самокат', 'Подарок маме'];

const pendingPiggyRequest = (state, piggyId) => state.requests.find((r) => r.piggyId === piggyId && r.status === 'pending');

/** Несколько копилок: список с выбором, внизу — поле суммы и «Положить» в выбранную. */
export function piggyCard(state, c) {
  const frozen = c.card.frozen;
  if (!c.piggies.some((p) => p.id === selectedPiggy[c.id])) selectedPiggy[c.id] = c.piggies[0]?.id;
  const sel = c.piggies.find((p) => p.id === selectedPiggy[c.id]);
  const rel = mainRelative(state);

  const items = c.piggies
    .map((p) => {
      const percent = pct(p.saved, p.target);
      const pending = pendingPiggyRequest(state, p.id);
      const selected = p.id === sel?.id;
      return `<li class="piggy-item ${selected ? 'is-selected' : ''}">
        <button class="piggy-item__main" data-action="piggy/select" data-child="${c.id}" data-id="${p.id}" aria-pressed="${selected}"
          aria-label="Копилка ${esc(p.goal)}: ${money(p.saved)} из ${money(p.target)}">
          ${ring(percent, `${percent}%`, '', 'ring--sm')}
          <span class="grow"><span class="piggy-item__goal">${esc(p.goal)}</span>
            <span class="piggy-item__amount">${money(p.saved)} из ${money(p.target)}</span></span>
          <span class="piggy-item__radio" aria-hidden="true"></span>
        </button>
        <div class="piggy-item__actions">${
          pending
            ? `<span class="badge" title="${esc(pending.reason)}">${icon('clock')} ${pending.kind === 'piggy-delete' ? 'Удаление' : 'Новая цель'} · ждём ${esc(rel.accusative)}</span>`
            : `<button class="link-btn" data-action="piggy/edit" data-child="${c.id}" data-id="${p.id}" ${frozen ? 'disabled' : ''}>Изменить цель</button>
               <button class="link-btn link-btn--danger" data-action="piggy/delete" data-child="${c.id}" data-id="${p.id}" ${frozen ? 'disabled' : ''}>Удалить</button>`
        }</div>
      </li>`;
    })
    .join('');

  return card({
    fit: true,
    title: 'Копилки',
    id: `piggy-${c.id}`,
    aside: frozen
      ? `<span class="badge badge--red">${icon('lock')} Счёт заблокирован</span>`
      : `<button class="section-link" data-action="piggy/new" data-child="${c.id}">${icon('plus')} Новая</button>`,
    body: c.piggies.length ? `<ul class="piggy-list">${items}</ul>` : emptyState('jar', 'Копилок пока нет — создай первую'),
    foot: sel
      ? `<form class="piggy-form" data-submit="child/piggy" data-child="${c.id}" data-piggy="${sel.id}">
          <span class="tiny muted">В копилку «${esc(sel.goal)}» · осталось ${money(Math.max(0, sel.target - sel.saved))}</span>
          <div class="input-row">
            <input class="input" name="amount" ${moneyAttrs} placeholder="Сумма, BYN" aria-label="Сумма в копилку" ${frozen ? 'disabled' : ''} />
            <button class="btn btn--primary" type="submit" ${frozen ? 'disabled' : ''}>Положить</button>
          </div></form>`
      : '',
  });
}

export function submitPiggy({ el, store }) {
  const res = store.dispatch('piggy/deposit', { childId: el.dataset.child, piggyId: el.dataset.piggy, amount: el.amount.value });
  if (!res.ok) return toast(res.reason, 'error');
  if (!res.completed) toast('Отложено в копилку · +2 балла'); // о сборе копилки сообщит уведомление
}

export const piggyActions = {
  'piggy/select': ({ el, rerender }) => {
    selectedPiggy[el.dataset.child] = el.dataset.id;
    rerender();
  },
  'piggy/new': ({ el, store, rerender }) => openPiggyCreate(store, el.dataset.child, rerender),
  'piggy/edit': ({ el, store }) => openPiggyEdit(store, el.dataset.child, el.dataset.id),
  'piggy/delete': ({ el, store }) => openPiggyDelete(store, el.dataset.child, el.dataset.id),
};

function openPiggyCreate(store, childId, rerender) {
  openSheet({
    title: 'Новая копилка',
    body: `<form class="stack" style="gap:14px" novalidate>
      <label class="field"><span class="field__label">На что копим</span>
        <input class="input" name="goal" maxlength="30" placeholder="Например, наушники" required /></label>
      <div class="chips">${PIGGY_IDEAS.map((g) => `<button type="button" class="chip" data-sheet-action="idea" data-value="${g}">${g}</button>`).join('')}</div>
      <label class="field"><span class="field__label">Сколько нужно собрать, BYN</span>
        <input class="input input--amount" name="target" ${moneyAttrs} placeholder="0,00" /></label>
      <p class="field__error" data-error></p>
      <button class="btn btn--primary btn--lg btn--block" type="submit">${icon('jar')} Создать копилку</button></form>`,
    onAction(name, el, api) {
      if (name === 'idea') api.el.querySelector('[name=goal]').value = el.dataset.value;
      if (name !== 'submit') return;
      const res = store.dispatch('piggy/create', { childId, goal: el.goal.value, target: el.target.value });
      if (!res.ok) return api.error(res.reason);
      selectedPiggy[childId] = res.id; // новая копилка сразу выбрана для пополнения
      rerender();
      api.close();
      toast('Копилка создана');
    },
  });
}

function openPiggyEdit(store, childId, piggyId) {
  const state = store.getState();
  const c = state.children[childId];
  const p = c.piggies.find((x) => x.id === piggyId);
  if (!p) return;
  const rel = mainRelative(state);
  const junior = c.cohort === 'junior';
  openSheet({
    title: `Цель «${p.goal}»`,
    body: `<form class="stack" style="gap:14px" novalidate>
      <p class="small muted">Собрано ${money(p.saved)} из ${money(p.target)}. Новая сумма — не меньше уже собранного.</p>
      <label class="field"><span class="field__label">Новая сумма цели, BYN</span>
        <input class="input input--amount" name="target" ${moneyAttrs} value="${String(p.target).replace('.', ',')}" /></label>
      ${
        junior
          ? `<div class="notice">${icon('hand')}<span>Изменить цель можно только с разрешения: ${esc(rel.dative)} придёт запрос.</span></div>`
          : `<div class="notice notice--warn">${icon('alert')}<span>Цель изменится сразу, без одобрения родителей. Если сумма станет равна собранной — копилка закроется и деньги вернутся на счёт.</span></div>`
      }
      <p class="field__error" data-error></p>
      <div class="sheet__actions"><button class="btn btn--ghost btn--lg" type="button" data-sheet-action="close">Отменить</button>
        <button class="btn btn--primary btn--lg" type="submit">${junior ? 'Отправить запрос' : 'Сохранить'}</button></div></form>`,
    onAction(name, el, api) {
      if (name !== 'submit') return;
      const res = store.dispatch('piggy/update', { childId, piggyId, target: el.target.value });
      if (!res.ok) return api.error(res.reason);
      api.close();
      if (res.pending) toast(`Запрос отправлен ${res.relative.dative}`);
      else if (!res.completed) toast('Цель изменена');
    },
  });
}

function openPiggyDelete(store, childId, piggyId) {
  const state = store.getState();
  const c = state.children[childId];
  const p = c.piggies.find((x) => x.id === piggyId);
  if (!p) return;
  const rel = mainRelative(state);
  const junior = c.cohort === 'junior';
  openSheet({
    title: 'Удалить копилку?',
    body: `<div class="stack" style="gap:14px">
      <p class="small">«${esc(p.goal)}»: собрано <b>${money(p.saved)}</b> из ${money(p.target)}.</p>
      ${
        junior
          ? `<div class="notice">${icon('hand')}<span>Удалить копилку можно только с разрешения: ${esc(rel.dative)} придёт запрос. После одобрения ${money(p.saved)} вернутся на счёт.</span></div>`
          : `<div class="notice notice--warn">${icon('alert')}<span>Копилка удалится сразу, без одобрения родителей. ${money(p.saved)} вернутся на счёт. Отменить это действие нельзя.</span></div>`
      }
      <div class="sheet__actions"><button class="btn btn--ghost btn--lg" type="button" data-sheet-action="close">Отменить</button>
        <button class="btn ${junior ? 'btn--primary' : 'btn--danger'} btn--lg" type="button" data-sheet-action="confirm">${junior ? 'Отправить запрос' : 'Удалить'}</button></div></div>`,
    onAction(name, el, api) {
      if (name !== 'confirm') return;
      const res = store.dispatch('piggy/delete', { childId, piggyId });
      api.close();
      if (!res.ok) return toast(res.reason, 'error');
      toast(res.pending ? `Запрос отправлен ${res.relative.dative}` : `Копилка удалена · ${money(res.returned)} на счёте`);
    },
  });
}

/** Статус последнего запроса денег — баннер на экране ребёнка. */
export function requestStatus(state, childId, now) {
  const r = state.requests.find((x) => x.childId === childId && x.kind === 'topup');
  if (!r) return '';
  const rel = state.relatives.find((x) => x.id === r.relativeId) ?? mainRelative(state);
  const what = `<b>${money(r.amount)}</b> · ${esc(r.reason)}`;
  if (r.status === 'sent') return `<div class="section notice" aria-live="polite">${icon('send')}<span>Запрос отправлен ${esc(rel.dative)}: ${what} · ${ago(r.ts, now)}</span></div>`;
  if (r.status === 'pending') return `<div class="section notice" aria-live="polite">${icon('clock')}<span>Ждём ответа ${esc(rel.genitive)}: ${what} · ${ago(r.ts, now)}</span></div>`;
  if (now - (r.resolvedAt ?? 0) > 10 * 60_000) return '';
  return r.status === 'approved'
    ? `<div class="section notice notice--green" aria-live="polite">${icon('check')}<span>${esc(rel.label)} ${rel.female ? 'одобрила' : 'одобрил'}: <b>+${money(r.amount)}</b> уже на карте</span></div>`
    : `<div class="section notice notice--red" aria-live="polite">${icon('x')}<span>${esc(rel.label)} ${rel.female ? 'отклонила' : 'отклонил'} запрос: ${what}</span></div>`;
}

/**
 * «Попросить у [родственник ▾]». По умолчанию выбран главный родственник.
 * Запрос не главному родственнику уходит, но в демо у него нет своего окна.
 */
export function openAskMoney(store, childId, { amount = '5', reason = 'На обед' } = {}) {
  const state = store.getState();
  const main = mainRelative(state);
  let relativeId = main.id;
  const pushText = (rel) => `${rel.label} получит push в Moby и сможет одобрить в один тап`;

  const relDropdown = dropdown({
    name: 'relative',
    value: main.id,
    label: 'У кого попросить',
    cls: 'dd--title dd--left',
    options: state.relatives.map((r) => ({
      value: r.id,
      label: r.label,
      display: r.genitive,
      sub: r.main ? `${r.name} · главный родственник` : r.name,
      avatar: r.label[0],
    })),
  });

  openSheet({
    title: 'Попросить деньги',
    titleHtml: `<span>Попросить у</span>${relDropdown}`,
    body: `<form class="stack" style="gap:14px" novalidate>
      <label class="field"><span class="field__label">Сколько нужно, BYN</span>
        <input class="input input--amount" name="amount" ${moneyAttrs} value="${amount}" required /></label>
      <div class="chips" aria-label="Быстрый выбор суммы">${['2', '5', '10', '20']
        .map((v) => `<button type="button" class="chip" aria-pressed="${v === amount}" data-sheet-action="amount" data-value="${v}">${v} BYN</button>`)
        .join('')}</div>
      <label class="field"><span class="field__label">На что</span>
        <input class="input" name="reason" maxlength="60" value="${esc(reason)}" required /></label>
      <div class="chips" aria-label="Быстрый выбор причины">${REQUEST_REASONS.map(
        (r) => `<button type="button" class="chip" aria-pressed="${r === reason}" data-sheet-action="reason" data-value="${esc(r)}">${esc(r)}</button>`,
      ).join('')}</div>
      <p class="field__error" data-error></p>
      <button class="btn btn--ray btn--lg btn--block" type="submit">Отправить запрос</button>
      <p class="muted tiny" style="text-align:center" data-push-hint>${esc(pushText(main))}</p>
    </form>`,
    onAction(name, el, api) {
      const form = api.el.querySelector('form');
      if (name === 'dd') {
        relativeId = el.dataset.value;
        const rel = store.getState().relatives.find((r) => r.id === relativeId);
        api.el.querySelector('[data-push-hint]').textContent = pushText(rel);
        return;
      }
      if (name === 'amount' || name === 'reason') {
        form[name].value = el.dataset.value;
        api.el.querySelectorAll(`[data-sheet-action=${name}]`).forEach((b) => b.setAttribute('aria-pressed', String(b === el)));
        return;
      }
      if (name === 'submit') {
        const res = store.dispatch('request/create', { childId, relativeId, amount: form.amount.value, reason: form.reason.value });
        if (!res.ok) return api.error(res.reason);
        api.close();
        toast(`Запрос отправлен ${res.relative.dative}`);
      }
    },
  });
}
