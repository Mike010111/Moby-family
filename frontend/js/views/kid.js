/**
 * Окно «Ребёнок 7–13» — Moby-Young (7–13), детский контур с жёсткими ограничениями.
 * До 14 лет нет самостоятельного доступа в СДБО — режим работает по согласию родителя,
 * P2P-переводов нет вовсе.
 */
import { icon } from '../ui/icons.js';
import { card, emptyState, txList } from '../ui/components.js';
import { openScanner, toast } from '../ui/overlay.js';
import { esc, money } from '../core/format.js';
import { HARD_BLOCKS, categoriesFor, spendable } from '../core/rules.js';
import { LESSONS, QR_MERCHANTS } from '../core/catalog.js';
import { mascotActions, mascotCard } from './mascot.js';
import { childHeader, mainRelative, myLimitsCard, openAskMoney, piggyActions, piggyCard, requestStatus, submitPiggy } from './shared.js';

const CHILD_ID = 'misha';

export const kidView = {
  childId: CHILD_ID,

  render(state, now) {
    const c = state.children[CHILD_ID];
    const frozen = c.card.frozen;

    return `<div class="app">
      ${childHeader(c)}
      ${mascotCard(c, now)}
      ${myLimitsCard(state, c, now)}

      <div class="section">
        <button class="btn btn--ray qr-cta" data-action="kid/openQr" ${frozen ? 'disabled' : ''}>
          <span class="qr-cta__icon">${icon('qr')}</span>
          <span><span class="qr-cta__title">Оплатить по QR · КРОК</span>${frozen ? '<span class="qr-cta__sub">Счёт заблокирован</span>' : ''}</span>
        </button>
      </div>

      <div class="section action-grid" aria-label="Действия">
        <button class="action-tile" data-action="kid/askMoney" ${frozen ? 'disabled title="Счёт заблокирован"' : ''}><span class="action-tile__icon">${icon('hand')}</span>Попросить деньги</button>
        <button class="action-tile" data-action="kid/scrollTo" data-target="piggy-${CHILD_ID}"><span class="action-tile__icon">${icon('jar')}</span>Копилка</button>
        <button class="action-tile action-tile--locked" disabled aria-disabled="true" title="Переводы доступны с 14 лет">
          <span class="action-tile__icon">${icon('lock')}</span>Переводы<br/>с 14 лет</button>
      </div>

      ${requestStatus(state, CHILD_ID, now)}
      ${permissionsCard(c)}
      ${piggyCard(state, c)}
      ${tasksCard(c)}
      ${lessonsCard(c)}
      ${card({ fit: true, title: 'Мои покупки', body: txList(c.tx, { now }) })}
    </div>`;
  },

  actions: {
    'kid/askMoney': ({ store }) => openAskMoney(store, CHILD_ID),
    'kid/openQr': ({ store }) => {
      const state = store.getState();
      openScanner({
        merchants: QR_MERCHANTS.junior,
        available: () => money(spendable(store.getState().children[CHILD_ID])),
        legalNote: `Мелкая бытовая сделка: ребёнок до 14 лет вправе совершать её самостоятельно (ст. 27 ГК РБ). ${mainRelative(state).label} увидит покупку сразу.`,
        onPay: (m) => store.dispatch('pay/qr', { childId: CHILD_ID, merchantId: m.id }),
      });
    },
    'kid/scrollTo': ({ el }) => document.getElementById(el.dataset.target)?.scrollIntoView({ behavior: 'smooth', block: 'center' }),
    'kid/completeTask': async ({ el, store }) => {
      const res = await store.dispatch('task/complete', { childId: CHILD_ID, taskId: el.dataset.task });
      res.ok ? toast('Отправлено на проверку') : toast(res.reason, 'error');
    },
    'kid/lesson': async ({ store }) => {
      const res = await store.dispatch('lesson/complete', { childId: CHILD_ID });
      res.ok ? toast('Урок пройден · +10 баллов') : toast(res.reason, 'info');
    },
    'child/piggy': submitPiggy,
    ...piggyActions,
    ...mascotActions,
  },
};

/* ───────────── Блоки ───────────── */

function permissionsCard(c) {
  const rows = categoriesFor(c).map((cat) => perm(cat.label, cat.icon, !!c.allow[cat.key]));
  rows.push(perm('Переводы — с 14 лет', 'send', false));
  for (const h of HARD_BLOCKS) rows.push(perm(h.label, h.icon, false));
  return card({ fit: true, title: 'Мои разрешения и запреты', body: `<ul class="perm-list">${rows.join('')}</ul>` });
}

const perm = (label, ic, on) => `<li class="perm ${on ? '' : 'perm--off'}">
  <span class="perm__icon">${icon(on ? ic : 'lock')}</span><span>${esc(label)}</span>
  <span class="perm__state">${on ? 'Можно' : 'Нельзя'}</span></li>`;

function tasksCard(c) {
  const open = c.tasks.filter((t) => t.status !== 'done');
  const body = open.length
    ? `<ul>${open
        .map(
          (t) => `<li class="task-row task-row--${t.status}">
        <span class="task-check">${t.status === 'review' ? icon('clock') : ''}</span>
        <span class="task-row__text">${esc(t.title)}<span class="task-row__reward">Награда ${money(t.reward)} + 5 баллов</span></span>
        ${
          t.status === 'open'
            ? `<button class="btn btn--primary btn--sm" data-action="kid/completeTask" data-task="${t.id}" ${c.card.frozen ? 'disabled' : ''}>Сделал!</button>`
            : '<span class="badge">Проверяют</span>'
        }
      </li>`,
        )
        .join('')}</ul>`
    : emptyState('check', 'Все задания выполнены');
  return card({
    fit: true,
    title: 'Задания',
    aside: c.card.frozen ? `<span class="badge badge--red">${icon('lock')} Счёт заблокирован</span>` : `<span class="badge">${open.length}</span>`,
    body,
  });
}

function lessonsCard(c) {
  const done = c.lessons.done;
  const finished = done >= LESSONS.length;
  return card({
    fit: true,
    title: 'Финансовая грамотность',
    aside: `<span class="badge">${done} из ${LESSONS.length}</span>`,
    body: `<ol class="lesson-list">${LESSONS.map((title, i) => {
      const state = i < done ? 'done' : i === done ? 'current' : 'next';
      return `<li class="lesson lesson--${state}"><span class="lesson__num">${state === 'done' ? icon('check') : i + 1}</span>${esc(title)}</li>`;
    }).join('')}</ol>`,
    foot: `<button class="btn btn--primary btn--block" data-action="kid/lesson" ${finished ? 'disabled' : ''}>${icon('book')} ${finished ? 'Все уроки пройдены' : `Пройти урок ${done + 1} · +10 баллов`}</button>`,
  });
}
