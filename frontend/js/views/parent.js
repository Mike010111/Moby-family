/**
 * Окно «Главный родственник» — Moby-Family, центр контроля семьи.
 */
import { bankIcon, icon } from '../ui/icons.js';
import { card, emptyState, limitList, lockedSwitch, moneyAttrs, progress, range, switchBtn, txList } from '../ui/components.js';
import { openSheet, toast } from '../ui/overlay.js';
import { ago, esc, int, money, pct } from '../core/format.js';
import {
  AUTOPAY_EVERY,
  COHORTS,
  DAYS_SHORT,
  HARD_BLOCKS,
  LIMIT_MAX_COUNT,
  LIMIT_UNITS,
  autopaySchedule,
  categoriesFor,
  category,
  openCategories,
  periodLabel,
  periodTitle,
} from '../core/rules.js';
import { GEO_PLACES, LESSONS } from '../core/catalog.js';

export const parentView = {
  render(state, now) {
    const pending = state.requests.filter((r) => r.status === 'pending');
    const child = state.children[state.ui.selectedChildId];

    return `<div class="app">
      ${header(pending.length)}
      ${accountCard(state.parent)}
      ${feedCard(state, now, pending.length)}
      <section class="section" aria-label="Дети">
        <div class="section-head" style="margin-top:4px"><h2 class="section-title">Дети</h2></div>
        ${childTabs(state)}
      </section>
      ${childCard(child)}
      <div class="section">
        <button class="btn btn--primary btn--lg btn--block" data-action="limit/create" data-child="${child.id}">${icon('plus')} Создать лимит</button>
      </div>
      ${card({ fit: true, title: 'Лимиты', aside: `<span class="badge">${child.limits.length}</span>`, body: limitList(child, { now, editable: true }) })}
      ${autopayCard(child, state)}
      ${categoriesCard(child)}
      ${radarCard(child, now)}
      ${tasksCard(child)}
      ${card({ fit: true, title: `Операции ${child.nameGen}`, body: txList(child.tx, { now }) })}
    </div>`;
  },

  actions: {
    'parent/approve': ({ el, store }) => {
      const res = store.dispatch('request/approve', { id: el.dataset.id });
      res.ok ? toast('Одобрено') : toast(res.reason, 'error');
    },
    'parent/decline': ({ el, store }) => {
      const res = store.dispatch('request/decline', { id: el.dataset.id });
      res.ok ? toast('Запрос отклонён', 'info') : toast(res.reason, 'error');
    },
    'parent/approveTask': ({ el, store }) => {
      const res = store.dispatch('task/approve', { childId: el.dataset.child, taskId: el.dataset.task });
      res.ok ? toast('Задание принято · награда выплачена') : toast(res.reason, 'error');
    },
    'parent/selectChild': ({ el, store }) => store.dispatch('ui/selectChild', { childId: el.dataset.child }),
    'parent/toggleAllow': ({ el, store }) => {
      const res = store.dispatch('parent/toggleAllow', { childId: el.dataset.child, key: el.dataset.key });
      if (!res.ok) toast(res.reason, 'error');
    },
    'parent/toggleFreeze': ({ el, store }) => {
      store.dispatch('parent/toggleFreeze', { childId: el.dataset.child });
      const frozen = store.getState().children[el.dataset.child].card.frozen;
      toast(frozen ? 'Счёт заблокирован: операции остановлены' : 'Счёт снова активен', frozen ? 'error' : 'ok');
    },
    'parent/refreshGeo': ({ el, store }) => store.dispatch('parent/refreshGeo', { childId: el.dataset.child }),
    'parent/scrollFeed': () => document.getElementById('parent-feed')?.scrollIntoView({ behavior: 'smooth', block: 'start' }),
    'parent/openTopUp': ({ el, store }) => openTopUp(store, el.dataset.child),
    'parent/openTask': ({ el, store }) => openTask(store, el.dataset.child),
    'parent/openAddChild': () => openAddChild(),
    'parent/openAutopay': ({ el, store }) => openAutopay(store, el.dataset.child),
    'limit/create': ({ el, store }) => openLimit(store, el.dataset.child),
    'limit/edit': ({ el, store }) => openLimit(store, el.dataset.child, el.dataset.id),
    'limit/delete': ({ el, store }) => {
      const res = store.dispatch('limit/delete', { childId: el.dataset.child, id: el.dataset.id });
      res.ok ? toast('Лимит удалён') : toast(res.reason, 'error');
    },
  },
};

/* ───────────── Блоки разметки ───────────── */

function header(pendingCount) {
  return `<header class="app-header">
    ${bankIcon()}
    <div class="app-header__titles"><div class="app-header__eyebrow">Moby-Family</div><h1 class="app-header__title">Семья</h1></div>
    <button class="icon-btn" data-action="parent/scrollFeed" aria-label="Уведомления: ${pendingCount} новых">
      ${icon('bell')}${pendingCount ? `<span class="icon-btn__dot">${pendingCount}</span>` : ''}</button>
  </header>`;
}

function accountCard(p) {
  return `<section class="section card card--hero card--account" aria-label="Мой счёт">
    <div class="row row--between"><span class="hero__label">Мой счёт · ${esc(p.name)}</span><span class="badge badge--glass">${icon('card')} ${p.cardMask}</span></div>
    <div class="hero__amount num">${money(p.balance).replace(' BYN', '')} <small>BYN</small></div>
    <div class="row" style="gap:6px;flex-wrap:wrap">
      <span class="badge badge--glass">${icon('star')} Мои баллы: ${int(p.points)}</span>
      <span class="badge badge--glass">${icon('layers')} ${esc(p.packageName)}</span>
    </div>
  </section>`;
}

function feedCard(state, now, pendingCount) {
  const isPending = (f) => f.kind === 'request' && state.requests.find((r) => r.id === f.requestId)?.status === 'pending';
  const items = [...state.feed].sort((a, b) => Number(isPending(b)) - Number(isPending(a)) || b.ts - a.ts);
  return card({
    fit: true,
    title: 'Запросы и события',
    id: 'parent-feed',
    aside: pendingCount ? `<span class="badge badge--red">${pendingCount} ждут решения</span>` : '',
    body: items.length ? `<ul class="feed" aria-live="polite">${items.map((f) => feedItem(f, state, now)).join('')}</ul>` : emptyState('bell', 'Пока тихо'),
  });
}

function feedItem(f, state, now) {
  const c = state.children[f.childId];
  const base = (iconName, extra = '') => `<li class="feed-item feed-item--${f.kind}">
      <span class="feed-item__icon">${icon(iconName)}</span>
      <div><div class="feed-item__title">${esc(f.title)}</div><div class="feed-item__text">${esc(f.text)} · ${ago(f.ts, now)}</div></div>
      ${extra}</li>`;

  if (f.kind === 'request') {
    const r = state.requests.find((x) => x.id === f.requestId);
    if (!r) return '';
    const ic = r.kind === 'p2p-over' ? 'send' : r.kind.startsWith('piggy') ? 'jar' : 'hand';
    if (r.status === 'pending') {
      return base(
        ic,
        `<div class="feed-item__actions">
          <button class="btn btn--primary btn--sm" data-action="parent/approve" data-id="${r.id}">${icon('check')} Одобрить</button>
          <button class="btn btn--ghost btn--sm" data-action="parent/decline" data-id="${r.id}">Отклонить</button></div>`,
      );
    }
    const label = { approved: ['badge--green', 'Одобрено'], declined: ['badge--red', 'Отклонено'], cancelled: ['', 'Отменён'] }[r.status];
    return base(ic, `<div class="feed-item__status"><span class="badge ${label[0]}">${label[1]}${r.resolution ? ` · ${esc(r.resolution)}` : ''}</span></div>`);
  }

  if (f.kind === 'task') {
    const t = c.tasks.find((x) => x.id === f.taskId);
    if (t?.status === 'review') {
      return base(
        'check',
        `<div class="feed-item__actions"><button class="btn btn--primary btn--sm" data-action="parent/approveTask" data-child="${c.id}" data-task="${t.id}" ${c.card.frozen ? 'disabled' : ''}>Принять и выплатить</button></div>`,
      );
    }
    return base('check', t?.status === 'done' ? `<div class="feed-item__status"><span class="badge badge--green">Награда выплачена</span></div>` : '');
  }

  return base({ alert: 'shield', info: 'receipt' }[f.kind] ?? 'info');
}

function childTabs(state) {
  return `<div class="child-tabs" role="tablist" aria-label="Выбор ребёнка">
    ${Object.values(state.children)
      .map(
        (c) => `<button class="child-tab" role="tab" aria-selected="${c.id === state.ui.selectedChildId}" data-action="parent/selectChild" data-child="${c.id}">
        <span class="avatar avatar--sm">${esc(c.name[0])}</span>
        <span><span class="child-tab__name">${esc(c.name)}, ${c.age}</span><span class="child-tab__meta">${COHORTS[c.cohort].label} · ${money(c.balance)}</span></span>
      </button>`,
      )
      .join('')}
    <button class="child-tab child-tab--add" data-action="parent/openAddChild" aria-label="Добавить ребёнка" title="Добавить ребёнка">${icon('plus')}</button>
  </div>`;
}

function childCard(c) {
  const frozen = c.card.frozen;
  return `<section class="section card card--hero" aria-label="Счёт ребёнка">
    <div class="row">
      <span class="avatar" style="background:rgba(255,255,255,.18)">${esc(c.name[0])}</span>
      <div class="grow"><div style="font-weight:700">${esc(c.name)}, ${c.age} лет</div>
        <div class="tiny" style="opacity:.8">${esc(c.card.title)} ${c.card.mask}</div></div>
      ${frozen ? `<span class="badge badge--white">${icon('lock')} Заблокирован</span>` : `<span class="badge badge--glass">${COHORTS[c.cohort].label}</span>`}
    </div>
    <div class="hero__amount num">${money(c.balance).replace(' BYN', '')} <small>BYN</small></div>
    <div class="hero-cells">
      <div class="hero-cell"><span>Баллы ребёнка (отдельно)</span><b>${int(c.points)}</b></div>
      <div class="hero-cell"><span>Копилки · ${c.piggies.length}</span><b>${money(c.piggies.reduce((s, p) => s + p.saved, 0))}</b></div>
    </div>
    <div class="row">
      <button class="btn btn--light btn--block" data-action="parent/openTopUp" data-child="${c.id}" ${frozen ? 'disabled' : ''}>${icon('plus')} Пополнить</button>
      <button class="btn btn--glass btn--block" data-action="parent/toggleFreeze" data-child="${c.id}">
        ${icon(frozen ? 'unlock' : 'lock')} ${frozen ? 'Разблокировать' : 'Заблокировать'}</button>
    </div>
  </section>`;
}

function autopayCard(c, state) {
  const a = c.autopay;
  const status = c.card.frozen
    ? `<span class="badge badge--red">${icon('lock')} Приостановлен</span>`
    : a.enabled
      ? `<span class="badge badge--green">Включён</span>`
      : `<span class="badge">Выключен</span>`;
  return card({
    fit: true, // высота по содержимому — без пустого места
    title: 'Автоперевод',
    aside: status,
    body: `<div class="autopay">
      <div class="autopay__amount">${money(a.amount)}</div>
      <div class="autopay__line">${icon('clock')}<span>${esc(autopaySchedule(a)[0].toUpperCase() + autopaySchedule(a).slice(1))}</span></div>
      <div class="autopay__line">${icon('arrowDown')}<span>Со счёта ${state.parent.cardMask} на счёт ${esc(c.nameGen)} ${c.card.mask}</span></div>
      ${c.card.frozen ? `<div class="notice notice--red">${icon('lock')}<span>Счёт ребёнка заблокирован — переводы не выполняются</span></div>` : ''}
    </div>`,
    foot: `<button class="btn btn--ghost btn--block" data-action="parent/openAutopay" data-child="${c.id}">${icon('pen')} Настроить</button>`,
  });
}

function categoriesCard(c) {
  const rows = categoriesFor(c).map((cat) => controlRow(c, cat));
  if (!COHORTS[c.cohort].p2pAllowed) {
    rows.push(`<div class="control-row"><span class="control-row__icon control-row__icon--red">${icon('send')}</span>
      <div class="control-row__text"><div class="control-row__label">Переводы</div><div class="control-row__hint">Недоступны до 14 лет</div></div>${lockedSwitch('Переводы')}</div>`);
  }
  for (const h of HARD_BLOCKS) {
    rows.push(`<div class="control-row"><span class="control-row__icon control-row__icon--red">${icon(h.icon)}</span>
      <div class="control-row__text"><div class="control-row__label">${h.label}</div><div class="control-row__hint">Запрещено</div></div>${lockedSwitch(h.label)}</div>`);
  }
  return card({ fit: true, title: 'Категории и доступ', body: rows.join('') });
}

function controlRow(c, cat) {
  const on = !!c.allow[cat.key];
  return `<div class="control-row">
    <span class="control-row__icon">${icon(cat.icon)}</span>
    <div class="control-row__text"><div class="control-row__label">${cat.label}</div><div class="control-row__hint">${on ? 'Разрешено' : 'Запрещено'}</div></div>
    ${switchBtn({ on, action: 'parent/toggleAllow', data: { child: c.id, key: cat.key }, label: cat.label })}
  </div>`;
}

function radarCard(c, now) {
  const low = c.battery <= 20;
  const others = GEO_PLACES.filter((p) => p.name !== 'Дом' && p.name !== c.geo.place);
  return card({
    title: `Где ${c.name}`,
    aside: `<button class="section-link" data-action="parent/refreshGeo" data-child="${c.id}">${icon('refresh')} Обновить</button>`,
    body: `<div class="radar-wrap">
      <div class="radar" role="img" aria-label="${esc(c.name)}: ${esc(c.geo.place)}, ${c.geo.distance} км от дома">
        <svg class="radar__grid" viewBox="0 0 100 100" aria-hidden="true">
          <g fill="none" stroke="rgba(35,52,200,.16)" stroke-width=".6">
            <circle cx="50" cy="50" r="48"/><circle cx="50" cy="50" r="32"/><circle cx="50" cy="50" r="16"/>
            <path d="M50 2v96M2 50h96" stroke-dasharray="1.5 2"/></g>
        </svg>
        <div class="radar__sweep"></div>
        ${others.map((p) => `<span class="radar__place" style="left:${p.x * 100}%;top:${p.y * 100}%" title="${esc(p.name)}"></span>`).join('')}
        <span class="radar__home">${icon('home')}</span>
        <div class="radar__marker" style="left:${c.geo.x * 100}%;top:${c.geo.y * 100}%"><span class="radar__pulse"></span><span class="radar__pin">${esc(c.name[0])}</span></div>
      </div></div>
      <div class="geo-meta">
        <div><div class="geo-meta__place">${icon('mapPin')} ${esc(c.geo.place)}</div>
          <div class="muted tiny">${c.geo.distance ? `${String(c.geo.distance).replace('.', ',')} км от дома` : 'Дома'} · ${ago(c.geo.updated, now)}</div></div>
        <span class="battery ${low ? 'battery--low' : ''}" aria-label="Заряд телефона ${c.battery}%">
          <span class="battery__body"><span class="battery__level" style="--level:${c.battery}%"></span></span>${c.battery}%</span>
      </div>
      ${low ? `<div class="notice notice--red" style="margin-top:10px">${icon('alert')}<span>Низкий заряд телефона — пришлём последнюю геопозицию перед отключением.</span></div>` : ''}`,
  });
}

function tasksCard(c) {
  const frozen = c.card.frozen;
  const statusText = { open: 'Ждёт выполнения', review: 'На проверке', done: 'Выполнено' };
  return card({
    fit: true,
    title: 'Задания',
    aside: `<button class="section-link" data-action="parent/openTask" data-child="${c.id}" ${frozen ? 'disabled title="Счёт заблокирован"' : ''}>${icon('plus')} Задание</button>`,
    body: `${
      c.tasks.length
        ? `<ul>${c.tasks
            .map(
              (t) => `<li class="task-row task-row--${t.status}">
          <span class="task-check">${t.status === 'done' ? icon('check') : t.status === 'review' ? icon('clock') : ''}</span>
          <span class="task-row__text"><span class="task-row__title">${esc(t.title)}</span><span class="task-row__reward">${statusText[t.status]} · ${money(t.reward)}</span></span>
          ${t.status === 'review' ? `<button class="btn btn--primary btn--sm" data-action="parent/approveTask" data-child="${c.id}" data-task="${t.id}" ${frozen ? 'disabled' : ''}>Принять</button>` : ''}
        </li>`,
            )
            .join('')}</ul>`
        : emptyState('check', 'Заданий нет')
    }`,
    foot: `<div class="row row--between small" style="margin-bottom:6px"><span class="row" style="gap:6px">${icon('book', 'width="16" height="16" style="color:var(--mt-blue)"')} Финграмотность</span>
        <b>${c.lessons.done} из ${LESSONS.length} уроков</b></div>
      ${progress(pct(c.lessons.done, LESSONS.length))}`,
  });
}

/* ───────────── Шиты ───────────── */

function openTopUp(store, childId) {
  const state = store.getState();
  const c = state.children[childId];
  openSheet({
    title: `Пополнить: ${c.name}`,
    body: `<form class="stack" style="gap:14px" novalidate>
      <label class="field"><span class="field__label">Сумма, BYN</span>
        <input class="input input--amount" name="amount" ${moneyAttrs} value="10" required /></label>
      <div class="chips">${['5', '10', '20', '50'].map((v) => `<button type="button" class="chip" data-sheet-action="preset" data-value="${v}">${v} BYN</button>`).join('')}</div>
      <p class="muted small">Со счёта ${state.parent.cardMask} · доступно ${money(state.parent.balance)}</p>
      <p class="field__error" data-error></p>
      <button class="btn btn--ray btn--lg btn--block" type="submit">Перевести</button></form>`,
    onAction(name, el, api) {
      if (name === 'preset') api.el.querySelector('[name=amount]').value = el.dataset.value;
      if (name === 'submit') {
        const res = store.dispatch('parent/topUp', { childId, amount: el.amount.value });
        if (!res.ok) return api.error(res.reason);
        api.close();
        toast('Перевод выполнен');
      }
    },
  });
}

function openTask(store, childId) {
  const c = store.getState().children[childId];
  openSheet({
    title: `Задание: ${c.name}`,
    body: `<form class="stack" style="gap:14px" novalidate>
      <label class="field"><span class="field__label">Что нужно сделать</span>
        <input class="input" name="title" maxlength="50" placeholder="Например, вынести мусор" required /></label>
      <div class="chips">${['Прочитать главу книги', 'Сделать уроки', 'Помочь с ужином'].map((t) => `<button type="button" class="chip" data-sheet-action="idea" data-value="${t}">${t}</button>`).join('')}</div>
      <label class="field"><span class="field__label">Награда, BYN</span>
        <input class="input" name="reward" ${moneyAttrs} value="2" required /></label>
      <p class="field__error" data-error></p>
      <button class="btn btn--primary btn--lg btn--block" type="submit">Отправить</button></form>`,
    onAction(name, el, api) {
      if (name === 'idea') api.el.querySelector('[name=title]').value = el.dataset.value;
      if (name === 'submit') {
        const res = store.dispatch('parent/addTask', { childId, title: el.title.value, reward: el.reward.value });
        if (!res.ok) return api.error(res.reason);
        api.close();
        toast('Задание отправлено');
      }
    },
  });
}

/**
 * Создание и изменение лимита: срок (1–50 + день/неделя/месяц), сумма, категории.
 * В списке — только открытые для счёта категории (и уже выбранные в лимите).
 */
function openLimit(store, childId, limitId) {
  const c = store.getState().children[childId];
  const editing = limitId ? c.limits.find((l) => l.id === limitId) : null;
  if (limitId && !editing) return toast('Лимит не найден', 'error');
  const draft = editing ? { ...editing, categories: [...editing.categories] } : { count: 1, unit: 'day', amount: '', categories: [] };
  const open = openCategories(c).map((x) => x.key);
  const shown = [...new Set([...open, ...draft.categories])].map(category);

  const countLabel = () => periodLabel({ count: draft.count, unit: draft.unit });
  // Как будет называться текущий период, если сохранить с этим сроком
  const periodPreview = () => {
    const start = editing && editing.count === draft.count && editing.unit === draft.unit ? editing.start : undefined;
    return `Текущий период: ${periodTitle({ count: draft.count, unit: draft.unit, start })}`;
  };
  const catsHint = () => (draft.categories.length ? `Выбрано: ${draft.categories.length}` : 'Не выбрано — лимит действует на все траты');

  openSheet({
    title: editing ? 'Изменить лимит' : 'Новый лимит',
    body: `<form class="stack" style="gap:16px" novalidate>
      <div class="field"><span class="field__label">Срок</span>
        <div class="range-wrap">
          <div class="range-head"><span class="small muted">Количество</span><span class="range-value" data-count-label>${countLabel()}</span></div>
          ${range({ id: 'limit-count', name: 'count', min: 1, max: LIMIT_MAX_COUNT, value: draft.count, label: 'Срок лимита' })}
          <div class="range-scale"><span>1</span><span>${LIMIT_MAX_COUNT}</span></div>
        </div>
        <div class="segmented" role="group" aria-label="Единица срока">${Object.entries(LIMIT_UNITS)
          .map(([k, u]) => `<button type="button" aria-pressed="${k === draft.unit}" data-sheet-action="unit" data-value="${k}">${u.label}</button>`)
          .join('')}</div>
        <span class="tiny muted" data-period-preview>${periodPreview()}</span></div>
      <label class="field"><span class="field__label">Сумма, BYN</span>
        <input class="input input--amount" name="amount" ${moneyAttrs} value="${editing ? String(editing.amount).replace('.', ',') : ''}" placeholder="0,00" /></label>
      <div class="field"><span class="field__label">Категории</span>
        <div class="chips">${shown
          .map(
            (cat) => `<button type="button" class="chip" aria-pressed="${draft.categories.includes(cat.key)}" data-sheet-action="cat" data-value="${cat.key}">
              ${icon(cat.icon)} ${esc(cat.label)}${open.includes(cat.key) ? '' : ' · запрещена'}</button>`,
          )
          .join('')}</div>
        <span class="tiny muted" data-cats-hint>${catsHint()}</span></div>
      <p class="field__error" data-error></p>
      ${
        editing
          ? `<div class="sheet__actions"><button class="btn btn--ghost btn--lg" type="button" data-sheet-action="close">Отменить</button>
             <button class="btn btn--primary btn--lg" type="submit">Сохранить</button></div>`
          : `<button class="btn btn--primary btn--lg btn--block" type="submit">Создать лимит</button>`
      }</form>`,
    onAction(name, el, api) {
      if (name === 'input' && el.name === 'count') {
        draft.count = Number(el.value);
        api.el.querySelector('[data-count-label]').textContent = countLabel();
        api.el.querySelector('[data-period-preview]').textContent = periodPreview();
        return;
      }
      if (name === 'unit') {
        draft.unit = el.dataset.value;
        api.el.querySelectorAll('[data-sheet-action=unit]').forEach((b) => b.setAttribute('aria-pressed', String(b === el)));
        api.el.querySelector('[data-count-label]').textContent = countLabel();
        api.el.querySelector('[data-period-preview]').textContent = periodPreview();
        return;
      }
      if (name === 'cat') {
        const key = el.dataset.value;
        draft.categories = draft.categories.includes(key) ? draft.categories.filter((k) => k !== key) : [...draft.categories, key];
        el.setAttribute('aria-pressed', String(draft.categories.includes(key)));
        api.el.querySelector('[data-cats-hint]').textContent = catsHint();
        return api.error('');
      }
      if (name !== 'submit') return;
      const payload = { childId, count: draft.count, unit: draft.unit, amount: el.amount.value, categories: draft.categories };
      const res = editing ? store.dispatch('limit/update', { ...payload, id: editing.id }) : store.dispatch('limit/create', payload);
      if (!res.ok) return api.error(res.reason);
      api.close();
      toast(editing ? 'Лимит сохранён' : 'Лимит создан');
    },
  });
}

/** Автоперевод — один на ребёнка, его можно только настроить (без категорий). */
function openAutopay(store, childId) {
  const c = store.getState().children[childId];
  const draft = { ...c.autopay };
  const scheduleExtra = () =>
    draft.every === 'week'
      ? `<div class="field"><span class="field__label">День недели</span><div class="weekdays">${DAYS_SHORT.map(
          (d, i) => `<button type="button" class="chip" aria-pressed="${i === draft.weekday}" data-sheet-action="weekday" data-value="${i}">${d}</button>`,
        ).join('')}</div></div>`
      : draft.every === 'month'
        ? `<label class="field"><span class="field__label">Число месяца (1–28)</span>
           <input class="input" name="monthDay" type="number" inputmode="numeric" min="1" max="28" step="1" value="${draft.monthDay}" /></label>`
        : '';

  openSheet({
    title: 'Автоперевод',
    body: `<form class="stack" style="gap:16px" novalidate>
      <div class="control-row" style="padding:0;border:0">
        <span class="control-row__icon">${icon('refresh')}</span>
        <div class="control-row__text"><div class="control-row__label">Автоперевод ${esc(c.nameGen)}</div><div class="control-row__hint" data-enabled-hint>${draft.enabled ? 'Включён' : 'Выключен'}</div></div>
        <button type="button" class="switch" role="switch" aria-checked="${draft.enabled}" aria-label="Автоперевод включён" data-sheet-action="enabled"></button>
      </div>
      <label class="field"><span class="field__label">Сумма, BYN</span>
        <input class="input input--amount" name="amount" ${moneyAttrs} value="${String(draft.amount).replace('.', ',')}" /></label>
      <div class="field"><span class="field__label">Периодичность</span>
        <div class="segmented" role="group" aria-label="Периодичность">${Object.entries(AUTOPAY_EVERY)
          .map(([k, label]) => `<button type="button" aria-pressed="${k === draft.every}" data-sheet-action="every" data-value="${k}">${label}</button>`)
          .join('')}</div></div>
      <div data-schedule-extra>${scheduleExtra()}</div>
      <p class="field__error" data-error></p>
      <div class="sheet__actions"><button class="btn btn--ghost btn--lg" type="button" data-sheet-action="close">Отменить</button>
        <button class="btn btn--primary btn--lg" type="submit">Сохранить</button></div></form>`,
    onAction(name, el, api) {
      if (name === 'enabled') {
        draft.enabled = !draft.enabled;
        el.setAttribute('aria-checked', String(draft.enabled));
        api.el.querySelector('[data-enabled-hint]').textContent = draft.enabled ? 'Включён' : 'Выключен';
        return;
      }
      if (name === 'every') {
        draft.every = el.dataset.value;
        api.el.querySelectorAll('[data-sheet-action=every]').forEach((b) => b.setAttribute('aria-pressed', String(b === el)));
        api.el.querySelector('[data-schedule-extra]').innerHTML = scheduleExtra();
        return;
      }
      if (name === 'weekday') {
        draft.weekday = Number(el.dataset.value);
        api.el.querySelectorAll('[data-sheet-action=weekday]').forEach((b) => b.setAttribute('aria-pressed', String(b === el)));
        return;
      }
      if (name !== 'submit') return;
      const res = store.dispatch('autopay/update', {
        childId,
        enabled: draft.enabled,
        amount: el.amount.value,
        every: draft.every,
        weekday: draft.weekday,
        monthDay: el.monthDay?.value ?? draft.monthDay,
      });
      if (!res.ok) return api.error(res.reason);
      api.close();
      toast('Автоперевод сохранён');
    },
  });
}

function openAddChild() {
  openSheet({
    title: 'Добавление ребёнка',
    body: `<form class="stack" style="gap:18px">
      <div class="field"><span class="field__label">Возраст ребёнка</span>
        <div class="segmented" role="group" aria-label="Возраст ребёнка">
          <button type="button" aria-pressed="true" data-sheet-action="seg">7–13 лет</button>
          <button type="button" aria-pressed="false" data-sheet-action="seg">14–17 лет</button></div></div>

      <fieldset class="field" style="border:0;padding:0;margin:0"><legend class="field__label" style="margin-bottom:6px">Выберите формат карты</legend>
        <div class="stack" style="gap:8px">
          <label class="radio-card"><input type="radio" name="cardFormat" value="digital" checked />
            <span><span class="radio-card__title">Цифровая карта</span><span class="radio-card__hint">Мгновенный выпуск, бесплатно, токен в Moby Pay</span></span></label>
          <label class="radio-card"><input type="radio" name="cardFormat" value="plastic" />
            <span><span class="radio-card__title">Пластиковая карта</span><span class="radio-card__hint">Доставка курьером или самовывоз, цифровая версия также будет доступна в приложении</span></span></label>
        </div></fieldset>

      <fieldset class="field" style="border:0;padding:0;margin:0"><legend class="field__label" style="margin-bottom:6px">Дополнительные возможности</legend>
        <label class="radio-card"><input type="checkbox" name="qr" checked />
          <span><span class="radio-card__title">Включить оплату по QR КРОК, бесплатно</span><span class="radio-card__hint">Ребёнок сможет платить по QR-коду через детский режим приложения Moby</span></span></label>
      </fieldset>

      <fieldset class="field" style="border:0;padding:0;margin:0"><legend class="field__label" style="margin-bottom:6px">Согласия законного представителя</legend>
        <div class="stack" style="gap:8px">
          <label class="radio-card"><input type="checkbox" required />
            <span class="radio-card__hint" style="margin:0">На обработку персональных данных несовершеннолетнего</span></label>
          <label class="radio-card"><input type="checkbox" required />
            <span class="radio-card__hint" style="margin:0">На доступ ребёнка к Moby в выбранном режиме</span></label>
        </div></fieldset>

      <button class="btn btn--ray btn--lg btn--block" type="submit">${icon('shieldCheck')} Подписать в Moby</button></form>`,
    onAction(name, el, api) {
      if (name === 'seg') {
        api.el.querySelectorAll('[data-sheet-action=seg]').forEach((b) => b.setAttribute('aria-pressed', String(b === el)));
      }
      if (name === 'submit') {
        if (!el.checkValidity()) return el.reportValidity();
        api.close();
        toast('Заявка подписана · в демо ребёнок не создаётся', 'info');
      }
    },
  });
}
