/**
 * Слой оверлеев внутри экрана смартфона: bottom-sheet, тосты, QR-сканер.
 * Живут вне скролл-контейнера, поэтому перерисовка экрана их не сбрасывает.
 */
import { icon } from './icons.js';
import { qrSvg } from './components.js';
import { esc, money } from '../core/format.js';
import { groupLabel } from '../core/rules.js';
import { CONFIG } from '../config.js';

let overlayRoot;
let toastRoot;
let active = null;

export function mountOverlays(screenEl) {
  overlayRoot = screenEl.querySelector('[data-overlay-root]');
  toastRoot = screenEl.querySelector('[data-toast-root]');
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && active) active.close();
  });
}

/** Тосты одного окна не переносятся в другое при смене роли. */
export function clearToasts() {
  toastRoot?.replaceChildren();
}

export function closeOverlay() {
  active?.close({ immediate: true });
}

/* ───────────── Bottom sheet ───────────── */
/**
 * @param {{title: string, titleHtml?: string, body: string, onAction?: (name: string, el: HTMLElement, api: object) => void, onMount?: (el: HTMLElement) => void}} cfg
 *   titleHtml — разметка заголовка (например, «Попросить у» + выпадающий список); title — доступное имя.
 *   Выбор в выпадающем списке приходит в onAction как ('dd', корень списка).
 */
export function openSheet({ title, titleHtml, body, onAction, onMount }) {
  closeOverlay();
  const wrap = document.createElement('div');
  wrap.className = 'overlay';
  wrap.innerHTML = `<section class="sheet" role="dialog" aria-modal="true" aria-label="${esc(title)}">
    <div class="sheet__handle"></div>
    <header class="sheet__head"><h2 class="sheet__title">${titleHtml ?? esc(title)}</h2>
      <button class="sheet__close" data-sheet-action="close" aria-label="Закрыть">${icon('x')}</button></header>
    <div class="sheet__body">${body}</div></section>`;

  const api = {
    el: wrap,
    close: ({ immediate = false } = {}) => {
      if (active !== api) return;
      active = null;
      if (immediate) return wrap.remove();
      wrap.classList.add('is-closing');
      removeAfterAnimation(wrap);
    },
    error: (text) => {
      const slot = wrap.querySelector('[data-error]');
      if (!slot) return;
      slot.textContent = text ?? '';
      if (text) slot.scrollIntoView({ block: 'nearest', behavior: 'smooth' }); // ошибка всегда на виду
    },
  };

  wrap.addEventListener('click', (e) => {
    if (e.target === wrap) return api.close();
    const el = e.target.closest('[data-sheet-action]');
    if (!el) return;
    const name = el.dataset.sheetAction;
    if (name === 'close') return api.close();
    onAction?.(name, el, api);
  });
  wrap.addEventListener('dd:change', (e) => onAction?.('dd', e.target, api));
  wrap.addEventListener('input', (e) => onAction?.('input', e.target, api));
  wrap.addEventListener('submit', (e) => {
    e.preventDefault();
    onAction?.('submit', e.target, api);
  });

  overlayRoot.append(wrap);
  active = api;
  onMount?.(wrap);
  wrap.querySelector('input:not([type=checkbox]), button.sheet__close')?.focus({ preventScroll: true });
  return api;
}

/** Удаляет узел после exit-анимации; таймер — страховка для prefers-reduced-motion и фоновых вкладок. */
function removeAfterAnimation(el, fallbackMs = 400) {
  const done = () => el.remove();
  el.addEventListener('animationend', (e) => e.target === el && done());
  setTimeout(done, fallbackMs);
}

/* ───────────── Toast ───────────── */
export function toast(text, tone = 'ok') {
  const el = document.createElement('div');
  el.className = `toast toast--${tone}`;
  el.setAttribute('role', 'status');
  el.innerHTML = `${icon(tone === 'error' ? 'alert' : tone === 'info' ? 'info' : 'check')}<span>${esc(text)}</span>`;
  toastRoot.append(el);
  setTimeout(() => {
    el.classList.add('is-leaving');
    removeAfterAnimation(el);
  }, 2800);
}

/* ───────────── QR-сканер КРОК ───────────── */
/**
 * Симуляция камеры: выбор демо-QR → «сканирование» → подтверждение → результат.
 * @param {{merchants: object[], legalNote?: string, balanceText: string, onPay: (m: object) => object | Promise<object>, onDone?: Function}} cfg
 */
export function openScanner({ merchants, legalNote, balanceText, onPay, onDone }) {
  closeOverlay();
  const wrap = document.createElement('div');
  wrap.className = 'scanner';
  wrap.setAttribute('role', 'dialog');
  wrap.setAttribute('aria-modal', 'true');
  wrap.setAttribute('aria-label', 'Оплата по QR КРОК');

  const api = {
    close: () => {
      if (active !== api) return;
      active = null;
      wrap.remove();
      onDone?.();
    },
  };

  const idle = () => {
    wrap.classList.remove('is-scanning');
    wrap.innerHTML = `
      <div class="scanner__top"><div><div class="scanner__title">Оплата по QR · КРОК</div>
        <div class="tiny muted">Доступно: ${esc(balanceText)}</div></div>
        <button class="scanner__close" data-scan="close" aria-label="Закрыть сканер">${icon('x')}</button></div>
      <div class="scanner__frame" aria-hidden="true">
        <span class="scanner__corner scanner__corner--tl"></span><span class="scanner__corner scanner__corner--tr"></span>
        <span class="scanner__corner scanner__corner--bl"></span><span class="scanner__corner scanner__corner--br"></span>
        <div class="scanner__ghost">${qrSvg('idle')}</div>
        <span class="scanner__line"></span>
      </div>
      <p class="scanner__hint">Наведите камеру на QR-код на кассе.<br/>Демо: выберите код, который «увидит» камера</p>
      <div class="scanner__codes">${merchants
        .map(
          (m) => `<button class="scan-code" data-scan="pick" data-id="${m.id}">${icon('qr')}
          <span class="scan-code__name">${esc(m.name)}<span class="scan-code__sub">${esc(groupLabel(m.mcc))}</span></span>
          <span class="scan-code__amount">${money(m.amount)}</span></button>`,
        )
        .join('')}</div>`;
  };

  const scanning = (m) => {
    wrap.classList.add('is-scanning');
    wrap.querySelector('.scanner__ghost').innerHTML = qrSvg(m.id);
    wrap.querySelector('.scanner__hint').textContent = 'Распознаём QR-код…';
    wrap.querySelectorAll('.scan-code').forEach((b) => (b.disabled = true));
    setTimeout(() => confirm(m), CONFIG.SCAN_DURATION_MS);
  };

  const confirm = (m) => {
    if (active !== api) return;
    wrap.querySelector('.scanner__codes').remove();
    wrap.querySelector('.scanner__hint').remove();
    wrap.insertAdjacentHTML(
      'beforeend',
      `<div class="scanner__result">
        <p class="muted small" style="text-align:center">${esc(m.name)}</p>
        <div class="result-amount">${money(m.amount)}</div>
        <p class="muted small" style="text-align:center;margin-bottom:14px">${esc(m.item)}</p>
        ${legalNote ? `<div class="notice" style="margin-bottom:14px">${icon('shieldCheck')}<span>${esc(legalNote)}</span></div>` : ''}
        <div class="row"><button class="btn btn--ghost btn--block" data-scan="reset">Отмена</button>
        <button class="btn btn--ray btn--block" data-scan="pay" data-id="${m.id}">Оплатить</button></div>
      </div>`,
    );
  };

  const result = (res) => {
    wrap.querySelector('.scanner__result').innerHTML = res.ok
      ? `<div class="result-icon">${icon('check')}</div>
         <div class="result-amount">−${money(res.merchant.amount)}</div>
         <p class="small" style="text-align:center;margin:6px 0 4px"><b>${esc(res.merchant.name)}</b></p>
         <p class="muted small" style="text-align:center;margin-bottom:${res.cashback ? 10 : 16}px">${esc(res.legal ?? '')} · +${res.earned} балл.</p>
         ${res.cashback ? `<div class="notice notice--green" style="margin-bottom:16px">${icon('sparkles')}<span>Кэшбэк ${res.cashback.pct}% от артефакта «${esc(res.cashback.artifact)}»: <b>+${money(res.cashback.value)}</b></span></div>` : ''}
         <button class="btn btn--primary btn--block" data-scan="close">Готово</button>`
      : `<div class="result-icon result-icon--fail">${icon('shield')}</div>
         <p style="text-align:center;font-weight:700;font-size:1.05rem">Оплата отклонена</p>
         <p class="muted small" style="text-align:center;margin:6px 0 16px">${esc(res.reason)}</p>
         <div class="row"><button class="btn btn--ghost btn--block" data-scan="reset">Другой QR</button>
         <button class="btn btn--primary btn--block" data-scan="close">Понятно</button></div>`;
  };

  wrap.addEventListener('click', (e) => {
    const el = e.target.closest('[data-scan]');
    if (!el) return;
    const m = merchants.find((x) => x.id === el.dataset.id);
    switch (el.dataset.scan) {
      case 'close':
        return api.close();
      case 'reset':
        return idle();
      case 'pick':
        return scanning(m);
      case 'pay':
        el.disabled = true;
        return Promise.resolve(onPay(m)).then(result);
      default:
    }
  });

  idle();
  overlayRoot.append(wrap);
  active = api;
  wrap.querySelector('.scanner__close').focus({ preventScroll: true });
  return api;
}
