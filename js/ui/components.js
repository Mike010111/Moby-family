/**
 * Шаблонные UI-примитивы. Возвращают HTML-строки; события навешиваются
 * делегированием в main.js через data-action / data-change.
 */
import { icon, mccIcon } from './icons.js';
import { ago, esc, money, moneyBare, signed } from '../core/format.js';
import { category, groupLabel, limitUsage, periodTitle } from '../core/rules.js';

/**
 * Карточка: заголовок, тело, необязательный футер. Ширина и отступы у всех одинаковые.
 *  - обычная — фиксированной высоты --card-h;
 *  - fit: true (списки) — по высоте содержимого, но не выше --card-h; дальше список прокручивается внутри.
 */
export function card({ title, aside = '', body, foot = '', cls = '', id, label, fit = false }) {
  return `<section class="section card ${fit ? 'card--fit' : ''} ${cls}" ${id ? `id="${id}"` : ''} aria-label="${esc(label ?? title)}">
    <header class="card__head"><h2 class="card__title">${esc(title)}</h2>${aside}</header>
    <div class="card__body">${body}</div>
    ${foot ? `<div class="card__foot">${foot}</div>` : ''}
  </section>`;
}

export const emptyState = (iconName, text) => `<div class="card__empty">${icon(iconName)}<span>${esc(text)}</span></div>`;

/** Тумблер с ARIA role=switch. */
export function switchBtn({ on, action, data = {}, label, disabled = false }) {
  return `<button class="switch" role="switch" aria-checked="${on}" aria-label="${esc(label)}"
    data-action="${action}" ${dataAttrs(data)} ${disabled ? 'disabled' : ''}></button>`;
}

/** Залоченный тумблер (запрет банка или закона). */
export function lockedSwitch(label) {
  return `<span class="switch switch--locked" role="img" aria-label="${esc(label)}: запрещено">${icon('lock')}</span>`;
}

/** Ползунок. live — id элемента, в который во время перетаскивания пишется подпись. */
export function range({ id, name, min, max, step = 1, value, live, label, format = 'int' }) {
  const fill = ((value - min) / (max - min)) * 100;
  return `<input id="${id}" ${name ? `name="${name}"` : ''} class="range" type="range" min="${min}" max="${max}" step="${step}" value="${value}"
    style="--fill:${fill}%" aria-label="${esc(label)}" data-live="${live}" data-format="${format}" />`;
}

/** Атрибуты поля ввода денег: не больше двух знаков после запятой (фильтр в main.js/overlay.js). */
export const moneyAttrs = 'type="text" inputmode="decimal" autocomplete="off" data-money';

export function progress(percent, mod = '') {
  const p = Math.max(0, Math.min(100, percent));
  return `<div class="progress" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${p}">
    <div class="progress__fill ${mod}" style="--p:${p}%"></div></div>`;
}

/** Кольцевой прогресс (копилка). */
export function ring(percent, label, sub, cls = '') {
  const r = 42;
  const c = 2 * Math.PI * r;
  const offset = c * (1 - Math.min(100, percent) / 100);
  return `<div class="ring ${cls}">
    <svg viewBox="0 0 100 100" aria-hidden="true">
      <circle class="ring__track" cx="50" cy="50" r="${r}"/>
      <circle class="ring__fill" cx="50" cy="50" r="${r}" stroke-dasharray="${c.toFixed(1)}" stroke-dashoffset="${offset.toFixed(1)}"/>
    </svg>
    <div class="ring__label">${label}${sub ? `<small>${sub}</small>` : ''}</div>
  </div>`;
}

export function txList(items, { limit = 20, now = Date.now() } = {}) {
  if (!items.length) return emptyState('receipt', 'Операций пока нет');
  return `<ul class="tx-list">${items
    .slice(0, limit)
    .map((t) => {
      const declined = t.status === 'declined';
      const sub = declined ? `Отклонено · ${t.note}` : [t.note ?? groupLabel(t.mcc), ago(t.ts, now)].filter(Boolean).join(' · ');
      return `<li class="tx ${declined ? 'tx--declined' : ''}">
        <span class="tx__icon">${declined ? icon('shield') : mccIcon(t.mcc)}</span>
        <span class="tx__body"><span class="tx__title">${esc(t.title)}</span><span class="tx__sub">${esc(sub)}</span></span>
        <span class="tx__amount ${t.amount > 0 && !declined ? 'tx__amount--in' : ''}">${signed(t.amount)}</span>
      </li>`;
    })
    .join('')}</ul>`;
}

/** Список лимитов. editable — с кнопками «Изменить»/«Удалить» (хаб родителя). */
export function limitList(child, { now = Date.now(), editable = false } = {}) {
  if (!child.limits.length) return emptyState('target', editable ? 'Лимитов пока нет — создайте первый' : 'Лимитов нет');
  return `<ul class="limit-list">${child.limits
    .map((l) => {
      const u = limitUsage(child, l, now);
      const cats = l.categories.length
        ? l.categories.map((k) => (child.allow[k] === false ? `<s title="Категория запрещена">${esc(category(k)?.label ?? k)}</s>` : esc(category(k)?.label ?? k))).join(', ')
        : 'Все категории';
      const status = u.exhausted
        ? `<b>Лимит исчерпан</b>${u.overBy > 0 ? `<span>сверх на ${money(u.overBy)}</span>` : ''}`
        : `<span>Потрачено ${money(u.spent)}</span><b>Осталось ${money(u.remaining)}</b>`;
      return `<li class="limit-row ${u.exhausted ? 'limit-row--exhausted' : ''}">
        <div class="limit-row__head"><span class="limit-row__title">${esc(periodTitle(l, now))}</span><span class="limit-row__amount">${moneyBare(l.amount)} BYN</span></div>
        <div class="limit-row__cats">${cats}</div>
        ${progress(u.percent, u.exhausted ? 'progress__fill--warn' : '')}
        <div class="limit-row__meta">${status}</div>
        ${
          editable
            ? `<div class="limit-row__actions">
          <button class="btn btn--ghost btn--sm" data-action="limit/edit" data-child="${child.id}" data-id="${l.id}">${icon('pen')} Изменить</button>
          <button class="btn btn--danger-soft btn--sm" data-action="limit/delete" data-child="${child.id}" data-id="${l.id}">${icon('trash')} Удалить</button></div>`
            : ''
        }
      </li>`;
    })
    .join('')}</ul>`;
}

/**
 * Кастомный выпадающий список (WAI-ARIA listbox). Поведение — ui/dropdown.js.
 * @param {{name: string, value: string, options: {value, label, sub?, display?}[], label: string, cls?: string, change?: string}} cfg
 *   display — текст на кнопке (например, родительный падеж «мамы»), label — в списке.
 */
export function dropdown({ name, value, options, label, cls = '', change = '' }) {
  const current = options.find((o) => o.value === value) ?? options[0];
  return `<div class="dd ${cls}" data-dd data-name="${name}" data-value="${esc(current.value)}" ${change ? `data-dd-change="${change}"` : ''}>
    <button type="button" class="dd__btn" aria-haspopup="listbox" aria-expanded="false" aria-label="${esc(label)}">
      <span class="dd__label">${esc(current.display ?? current.label)}</span>${icon('chevronDown', 'class="dd__chev"')}
    </button>
    <ul class="dd__menu" role="listbox" aria-label="${esc(label)}" hidden>
      ${options
        .map(
          (o) => `<li><button type="button" class="dd__option" role="option" aria-selected="${o.value === current.value}"
            data-value="${esc(o.value)}" data-display="${esc(o.display ?? o.label)}">
            ${o.avatar ? `<span class="avatar avatar--sm">${esc(o.avatar)}</span>` : ''}
            <span>${esc(o.label)}${o.sub ? `<span class="dd__option-sub">${esc(o.sub)}</span>` : ''}</span>
            ${icon('check', 'class="dd__check"')}</button></li>`,
        )
        .join('')}
    </ul>
  </div>`;
}

export function statusBar(now = Date.now()) {
  const t = new Date(now).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
  return `<div class="status-bar" aria-hidden="true"><span class="num">${t}</span>
    <span class="status-bar__icons">${icon('signal')}${icon('wifi')}${icon('battery')}</span></div>`;
}

/**
 * Псевдо-QR (детерминированный по seed): 25×25 модулей с тремя finder-паттернами.
 * Только визуализация для прототипа — не кодирует данные.
 */
export function qrSvg(seed = 'moby', color = '#141416', hole = '#fff') {
  const n = 25;
  let h = 2166136261;
  for (const ch of seed) h = Math.imul(h ^ ch.charCodeAt(0), 16777619);
  const rnd = () => {
    h ^= h << 13;
    h ^= h >>> 17;
    h ^= h << 5;
    return (h >>> 0) / 4294967296;
  };
  const inFinder = (x, y) => (x < 8 && y < 8) || (x > n - 9 && y < 8) || (x < 8 && y > n - 9);
  let cells = '';
  for (let y = 0; y < n; y += 1) {
    for (let x = 0; x < n; x += 1) {
      if (!inFinder(x, y) && rnd() > 0.52) cells += `<rect x="${x}" y="${y}" width="1" height="1"/>`;
    }
  }
  const finder = (x, y) =>
    `<rect x="${x}" y="${y}" width="7" height="7" rx="1.2"/><rect x="${x + 1}" y="${y + 1}" width="5" height="5" rx="0.8" fill="${hole}"/><rect x="${x + 2}" y="${y + 2}" width="3" height="3" rx="0.5"/>`;
  return `<svg viewBox="-1 -1 ${n + 2} ${n + 2}" fill="${color}" shape-rendering="crispEdges" aria-hidden="true">${cells}${finder(0, 0)}${finder(n - 7, 0)}${finder(0, n - 7)}</svg>`;
}

export function dataAttrs(data) {
  return Object.entries(data)
    .map(([k, v]) => `data-${k}="${esc(v)}"`)
    .join(' ');
}
