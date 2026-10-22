/**
 * Точка входа: связывает Store, окна ролей и оболочку стенда.
 *
 *   ┌──────────── Store (единый State) ────────────┐
 *   │ parent · relatives · children · requests ·   │
 *   │ feed · log                                   │
 *   └───────▲──────────────────────────┬───────────┘
 *           │ dispatch()               │ subscribe()
 *   ┌───────┴───────┐          ┌───────▼────────┐
 *   │ View handlers │          │ renderScreen() │ → parent | kid | teen
 *   └───────────────┘          │ renderShell()  │ → вкладки жюри
 *                              └────────────────┘
 */
import { Store } from './core/store.js';
import { reducers } from './core/actions.js';
import { createInitialState } from './core/initial-state.js';
import { parentView } from './views/parent.js';
import { kidView } from './views/kid.js';
import { teenView } from './views/teen.js';
import { clearToasts, closeOverlay, mountOverlays, toast } from './ui/overlay.js';
import { statusBar } from './ui/components.js';
import { initDropdowns } from './ui/dropdown.js';
import { BANK_ICON_URL, markBankIconMissing, mobyMark } from './ui/icons.js';
import { handleImageError } from './views/mascot.js';
import { initShell, renderShell, roleFromHash } from './ui/shell.js';
import { clock } from './core/format.js';
import { imageUrls } from './core/artifacts.js';

const views = { parent: parentView, kid: kidView, teen: teenView };
const actionHandlers = Object.assign({}, ...Object.values(views).map((v) => v.actions));
const changeHandlers = Object.assign({}, ...Object.values(views).map((v) => v.changes ?? {}));

const store = new Store(reducers, createInitialState);
const screen = document.getElementById('screen');
const scroll = document.getElementById('screen-scroll');
let currentRole = null;

/* ───────────── Рендер экрана смартфона ───────────── */
function renderScreen() {
  const state = store.getState();
  const now = Date.now();
  const role = state.ui.role;
  const view = views[role];
  const roleChanged = role !== currentRole;

  if (roleChanged && currentRole) {
    views[currentRole].onLeave?.();
    closeOverlay();
    clearToasts();
  }

  const focus = rememberFocus();
  const top = scroll.scrollTop;
  const inner = [...scroll.querySelectorAll('.card__body')].map((b) => b.scrollTop);

  scroll.innerHTML = statusBar(now) + view.render(state, now);
  reuseImages(scroll);
  scroll.scrollTop = roleChanged ? 0 : top;
  if (!roleChanged) scroll.querySelectorAll('.card__body').forEach((b, i) => (b.scrollTop = inner[i] ?? 0));

  if (roleChanged) history.replaceState(null, '', `#${role}`);
  restoreFocus(focus);
  currentRole = role;
  deliverInbox(view);
}

/**
 * Картинки карты (фон и белка) не создаются заново при каждой перерисовке:
 * в разметку подставляется уже загруженный и декодированный узел с тем же src.
 * Поэтому смена вкладки или действие на экране не дают ни одного «пустого» кадра.
 */
const imagePool = new Map(); // src → HTMLImageElement

function reuseImages(root) {
  root.querySelectorAll('img[data-reuse]').forEach((fresh) => {
    const src = fresh.getAttribute('src');
    const kept = imagePool.get(src);
    if (!kept || kept === fresh) return void imagePool.set(src, fresh);
    for (const { name } of [...kept.attributes]) if (!fresh.hasAttribute(name)) kept.removeAttribute(name);
    for (const { name, value } of [...fresh.attributes]) if (kept.getAttribute(name) !== value) kept.setAttribute(name, value);
    fresh.replaceWith(kept);
  });
}

/** Уведомления ребёнку (одобрения, отказы, блокировка) — тостом при открытии его окна. */
function deliverInbox(view) {
  if (!view.childId) return;
  const unseen = store.getState().children[view.childId].inbox.filter((n) => !n.seen);
  if (!unseen.length) return;
  unseen.reverse().forEach((n, i) => setTimeout(() => toast(n.text, n.tone), 150 + i * 350));
  queueMicrotask(() => store.dispatch('ui/markSeen', { childId: view.childId }));
}

/** Сохраняем фокус между перерисовками (клавиатурная доступность). */
function rememberFocus() {
  const el = document.activeElement;
  if (!el || !scroll.contains(el)) return null;
  if (el.id) return `#${CSS.escape(el.id)}`;
  const attrs = Object.entries(el.dataset).map(([k, v]) => `[data-${k.replace(/[A-Z]/g, (m) => `-${m.toLowerCase()}`)}="${CSS.escape(v)}"]`);
  if (el.name) attrs.push(`[name="${CSS.escape(el.name)}"]`);
  return attrs.length ? attrs.join('') : null;
}

function restoreFocus(selector) {
  if (selector) scroll.querySelector(selector)?.focus({ preventScroll: true });
}

/* ───────────── Делегирование событий экрана ───────────── */
scroll.addEventListener('click', (event) => {
  const el = event.target.closest('[data-action]');
  if (!el || el.disabled) return;
  const handler = actionHandlers[el.dataset.action];
  if (!handler) return console.warn(`[ui] Нет обработчика для ${el.dataset.action}`);
  handler({ el, store, event, rerender: renderScreen });
});

scroll.addEventListener('submit', (event) => {
  const form = event.target.closest('form[data-submit]');
  if (!form) return;
  event.preventDefault();
  actionHandlers[form.dataset.submit]?.({ el: form, store, event, rerender: renderScreen });
});

scroll.addEventListener('dd:change', (event) => {
  const root = event.target;
  changeHandlers[root.dataset.ddChange]?.({ el: root, value: event.detail.value, store, rerender: renderScreen });
});

/* ───────────── Глобальные поля ввода (экран и шиты) ───────────── */
document.addEventListener('input', (event) => {
  const el = event.target;

  // Деньги: только цифры и один разделитель, не больше двух знаков после запятой
  if (el.matches('input[data-money]')) {
    const caret = el.selectionStart;
    const before = el.value;
    let v = before.replace(/[^\d.,]/g, '').replace(/\./g, ',');
    const [int, ...rest] = v.split(',');
    v = rest.length ? `${int},${rest.join('').slice(0, 2)}` : int;
    if (v !== before) {
      el.value = v;
      const pos = Math.max(0, (caret ?? v.length) - (before.length - v.length));
      el.setSelectionRange(pos, pos);
    }
  }

  // Ползунки: заливка трека индиго до бегунка
  if (el.matches('input[type=range]')) {
    const min = Number(el.min);
    const max = Number(el.max);
    el.style.setProperty('--fill', `${((el.value - min) / (max - min)) * 100}%`);
  }
});

/* ───────────── Иконка банка: assets/icon.png или векторный запасной знак ───────────── */
document.addEventListener(
  'error',
  (event) => {
    const img = event.target;
    if (!(img instanceof HTMLImageElement)) return;
    if (handleImageError(img)) return; // белка и артефакты: запасные картинки
    if (img.dataset.fallback !== 'bank') return;
    markBankIconMissing();
    document.querySelectorAll('img[data-fallback=bank]').forEach((i) => {
      i.outerHTML = mobyMark(Number(i.getAttribute('width')) || 40, 'bank-icon');
    });
  },
  true,
);
document.addEventListener(
  'load',
  (event) => {
    if (event.target instanceof HTMLImageElement && event.target.dataset.fallback === 'bank') {
      document.querySelector('link[rel=icon]')?.setAttribute('href', BANK_ICON_URL);
    }
  },
  true,
);

/* ───────────── Старт ───────────── */
mountOverlays(screen);
initDropdowns();
initShell({
  store,
  onReset: () => {
    closeOverlay();
    store.reset();
    toast('Демо-сессия сброшена', 'info');
  },
});

const hashRole = roleFromHash();
if (hashRole) store.dispatch('ui/setRole', { role: hashRole });
window.addEventListener('hashchange', () => {
  const role = roleFromHash();
  if (role) store.dispatch('ui/setRole', { role });
});

/* ───────────── Предзагрузка: первый экран показывается уже с картинками и шрифтами ───────────── */
/** Ссылки на декодированные картинки держим всё время работы — браузер не выгружает их из памяти. */
const decodedImages = [];

function preloadImages(urls) {
  return Promise.all(
    urls.map((src) => {
      const img = new Image();
      img.decoding = 'async';
      img.src = src;
      decodedImages.push(img);
      imagePool.set(src, img); // первая отрисовка сразу возьмёт декодированный узел
      return img.decode().catch(() => {}); // нет файла — сработает запасной вариант в mascot.js
    }),
  );
}

const cardImages = Object.values(store.getState().children).map((c) => c.card.image);
const assetsReady = Promise.all([preloadImages([...new Set([...cardImages, ...imageUrls()])]), document.fonts?.ready]);
// Медленная сеть не должна держать пустой экран дольше 3 с: дальше догрузится в фоне
await Promise.race([assetsReady, new Promise((resolve) => setTimeout(resolve, 3000))]);

store.subscribe((state, action) => {
  if (action.type === 'ui/markSeen') return; // визуально ничего не меняется
  renderScreen();
  renderShell(state);
});
renderScreen();
renderShell(store.getState());

// Часы в статус-баре без полной перерисовки
setInterval(() => {
  const t = scroll.querySelector('.status-bar .num');
  if (t) t.textContent = clock();
}, 30_000);

// Для отладки из консоли: window.__moby.getState()
window.__moby = store;
