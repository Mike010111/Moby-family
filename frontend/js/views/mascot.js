/**
 * Карта учащегося с маскотом-белкой и магазин артефактов (окна 7–13 и 14–17).
 *
 * Белка накладывается на «голую» карту так же, как в образце student_card_example:
 * координаты подобраны по образцу в процентах от карты, поэтому совпадают при любом размере.
 */
import { icon } from '../ui/icons.js';
import { artifactArt } from '../ui/artifact-art.js';
import { openSheet, toast } from '../ui/overlay.js';
import { esc, int, money, plural } from '../core/format.js';
import { category } from '../core/rules.js';
import {
  ARTIFACTS,
  CYCLE,
  RARITY,
  SHOP,
  SLOTS,
  artifactImage,
  categoryLabel,
  cycleWeeks,
  isActive,
  mascotImage,
  nextSlot,
  nextSlotWeekStart,
  offerState,
  renewOffer,
  weekEnd,
} from '../core/artifacts.js';

const DAY = 86_400_000;
/** Ребёнок, у которого только что сменился артефакт: белка появится с анимацией. */
let justEquipped = null;

/** Файлы, которых пока нет в assets: дальше рисуем запасной вариант без повторных запросов. */
const missing = new Set();

/**
 * Обработчик ошибки загрузки картинок карты и магазина (вызывается из main.js).
 * @returns {boolean} обработано ли изображение
 */
export function handleImageError(img) {
  if (img.dataset.fallbackSrc) {
    missing.add(img.getAttribute('src'));
    img.src = img.dataset.fallbackSrc;
    delete img.dataset.fallbackSrc;
    return true;
  }
  if (img.dataset.fallback === 'artifact') {
    missing.add(img.getAttribute('src'));
    img.outerHTML = artifactArt(img.dataset.id, img.alt);
    return true;
  }
  return false;
}

const dateShort = (ts) => new Date(ts).toLocaleDateString('ru-RU', { day: 'numeric', month: 'long' });

/* ───────────── Карта с маскотом + строка баланса ───────────── */

export function mascotCard(c, now = Date.now()) {
  const img = mascotImage(c, now);
  if (img.fallback && missing.has(img.src)) {
    img.src = img.fallback;
    delete img.fallback;
  }
  const hands = c.artifacts.owned.hands;
  const wearing = Object.values(c.artifacts.owned)
    .filter((o) => isActive(o, now))
    .map((o) => o.name.toLowerCase());
  const animate = justEquipped === c.id;
  if (animate) justEquipped = null;

  return `<section class="section student-card" aria-label="${esc(c.card.title)} ${esc(c.card.holder)}, ${esc(c.card.mask)}. Белка: ${esc(wearing.join(', ') || 'без артефактов')}">
      <img class="student-card__bg" src="${c.card.image}" alt="" draggable="false" decoding="sync" data-reuse />
      <img class="student-card__mascot ${animate ? 'is-new' : ''}" src="${img.src}" ${img.fallback ? `data-fallback-src="${img.fallback}"` : ''}
        alt="" draggable="false" decoding="sync" data-reuse data-mascot="${esc(isActive(hands, now) ? hands.id : 'base')}" />
      ${c.card.frozen ? `<div class="frozen-veil">${icon('lock')}<span>Счёт заблокирован</span><span class="tiny muted" style="font-weight:500">Баллы и магазин артефактов работают</span></div>` : ''}
    </section>
    <div class="section">
      <button class="btn btn--primary btn--block shop-btn" data-action="mascot/shop" data-child="${c.id}">
        ${icon('sparkles')} Магазин артефактов <span class="shop-btn__points">${icon('star')} ${int(c.points)} баллов</span></button>
    </div>
    <section class="section balance-panel" aria-label="Мои деньги">
      <span class="balance-panel__icon">${icon('wallet')}</span>
      <span class="grow"><span class="balance-panel__label">Мои деньги</span><span class="balance-panel__hint">На карте ${esc(c.card.mask)}</span></span>
      <b class="balance-panel__amount num">${money(c.balance)}</b>
    </section>`;
}

/* ───────────── Магазин ───────────── */

export const mascotActions = {
  'mascot/shop': ({ el, store }) => openShop(store, el.dataset.child),
};

function openShop(store, childId) {
  let confirmId = null; // артефакт, для которого ждём подтверждения замены
  const child = () => store.getState().children[childId];

  const sheet = openSheet({
    title: 'Магазин артефактов',
    body: renderShop(child(), Date.now(), confirmId),
    async onAction(name, el, api) {
      const refresh = () => {
        api.el.querySelector('.sheet__body').innerHTML = renderShop(child(), Date.now(), confirmId);
      };
      if (name === 'buy') {
        const id = el.dataset.id;
        const offer = offerState(child(), id);
        if (offer.status === 'available' && offer.replaces && confirmId !== id) {
          confirmId = id; // первый тап — спросить про замену
          return refresh();
        }
        const res = await store.dispatch('artifact/buy', { childId, id });
        if (!res.ok) {
          confirmId = null;
          refresh();
          return toast(res.reason, 'error');
        }
        justEquipped = childId;
        api.close();
        toast(`Белка взяла «${res.artifact.name}» · ${res.artifact.cashback}% кэшбэк: ${categoryLabel(res.artifact.category)}`);
        document.querySelector('.student-card')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
        return;
      }
      if (name === 'cancel') {
        confirmId = null;
        return refresh();
      }
      if (name === 'renew') {
        const res = await store.dispatch('artifact/renew', { childId });
        if (!res.ok) return toast(res.reason, 'error');
        justEquipped = childId;
        api.close();
        toast('Артефакт продлён ещё на цикл');
      }
    },
  });
  return sheet;
}

function renderShop(c, now, confirmId) {
  const art = c.artifacts;
  const order = CYCLE[c.cohort];
  const weekIndex = order.indexOf(art.weekSlot);
  const daysLeft = Math.max(1, Math.ceil((weekEnd(art) - now) / DAY));
  const renew = renewOffer(c, now);
  const items = (SHOP[art.weekSlot] ?? []).map((id) => artifactCard(c, id, now, confirmId)).join('');

  return `
    <section class="shop-week" aria-label="Неделя цикла">
      <div class="shop-week__top">
        <span class="shop-week__icon">${icon(SLOTS[art.weekSlot].icon)}</span>
        <div class="grow"><div class="shop-week__eyebrow">Сейчас в магазине · неделя ${weekIndex + 1} из ${order.length}</div>
          <h3 class="shop-week__title">${SLOTS[art.weekSlot].week}</h3></div>
      </div>
      <ol class="cycle" aria-label="Цикл артефактов: ${cycleWeeks(c.cohort)} недели">
        ${order
          .map((slot, i) => {
            const own = art.owned[slot];
            const st = i === weekIndex ? 'now' : isActive(own, now) ? 'owned' : 'empty';
            return `<li class="cycle__step cycle__step--${st}" title="${SLOTS[slot].label}">
              <span class="cycle__dot">${icon(SLOTS[slot].icon)}</span><span class="cycle__label">${SLOTS[slot].label}</span></li>`;
          })
          .join('')}
      </ol>
      <p class="shop-week__meta">${icon('clock')} Ещё ${daysLeft} ${plural(daysLeft, ['день', 'дня', 'дней'])} · дальше — ${SLOTS[nextSlot(art)].next}</p>
    </section>

    <div class="shop-points">
      <span>Мои баллы</span><b class="num">${icon('star')} ${int(c.points)}</b>
    </div>

    ${renew ? renewBlock(c, renew) : ''}

    <div class="shop-grid">${items}</div>

    <section class="shop-owned" aria-label="Артефакты белки">
      <h3 class="shop-owned__title">Сейчас на белке</h3>
      <ul>${order.map((slot) => ownedRow(c, slot, now)).join('')}</ul>
    </section>

    <details class="shop-help">
      <summary>${icon('info')} Как работают артефакты</summary>
      <ul>
        <li>Каждую неделю в магазине новый вид артефактов: ${order.map((s) => SLOTS[s].label.toLowerCase()).join(' → ')}.</li>
        <li>Купленный артефакт сразу появляется на белке на твоей карте и даёт кэшбэк на свою категорию целый цикл — ${cycleWeeks(c.cohort)} ${plural(cycleWeeks(c.cohort), ['неделю', 'недели', 'недель'])}.</li>
        <li>Чем реже артефакт, тем больше кэшбэк: обычный — 3%, редкий — 4%, легендарный — 5%.</li>
        <li>Передумал? Купи другой этого же вида — он заменит прежний. Прежний вернётся в магазин только в следующем цикле.</li>
        <li>Когда цикл закончится, артефакт можно оставить на его неделе: кэшбэк станет на 1% меньше, но не меньше 3%.</li>
        <li>Баллы начисляются за покупки, задания и уроки финансовой грамотности.</li>
      </ul>
    </details>`;
}

function artifactCard(c, id, now, confirmId) {
  const a = ARTIFACTS[id];
  const r = RARITY[a.rarity];
  const cat = category(a.category[c.cohort]);
  const offer = offerState(c, id, now);
  const confirming = confirmId === id && offer.status === 'available';

  let action;
  switch (offer.status) {
    case 'equipped':
      action = `<span class="artifact__state artifact__state--on">${icon('check')} На белке</span>`;
      break;
    case 'soldout':
      action = `<span class="artifact__state">${icon('lock')} Недоступен до конца недели</span>`;
      break;
    case 'nopoints':
      action = `<span class="artifact__state">${icon('star')} Нужно ещё ${offer.lack}</span>`;
      break;
    default:
      action = confirming
        ? `<div class="artifact__confirm"><span>Заменит «${esc(offer.replaces.name)}» — он станет недоступен до конца недели</span>
             <button class="btn btn--primary btn--sm btn--block" data-sheet-action="buy" data-id="${id}">Заменить · ${offer.price}</button>
             <button class="link-btn" data-sheet-action="cancel">Отмена</button></div>`
        : `<button class="btn btn--primary btn--sm btn--block" data-sheet-action="buy" data-id="${id}">${icon('star')} ${offer.price} баллов</button>`;
  }

  return `<article class="artifact artifact--${a.rarity} ${offer.status === 'equipped' ? 'is-equipped' : ''} ${offer.status === 'soldout' ? 'is-soldout' : ''}">
    <div class="artifact__img">
      ${missing.has(artifactImage(id)) ? artifactArt(id, esc(a.name)) : `<img src="${artifactImage(id)}" alt="${esc(a.name)}" decoding="sync" data-fallback="artifact" data-id="${id}" />`}
    </div>
    <span class="rarity rarity--${a.rarity}">${r.label}</span>
    <h4 class="artifact__name">${esc(a.name)}</h4>
    <div class="artifact__perk"><b>${r.cashback}%</b> кэшбэк<span>${icon(cat.icon)} ${esc(cat.label)}</span></div>
    ${action}
  </article>`;
}

function ownedRow(c, slot, now) {
  const art = c.artifacts;
  const own = art.owned[slot];
  const s = SLOTS[slot];
  if (isActive(own, now)) {
    return `<li class="owned">
      <span class="owned__icon owned__icon--${own.rarity}">${icon(s.icon)}</span>
      <div class="grow"><div class="owned__name">${esc(own.name)} <span class="rarity rarity--${own.rarity} rarity--sm">${RARITY[own.rarity].label}</span></div>
        <div class="owned__meta">${own.cashback}% · ${esc(categoryLabel(own.category))} · до ${dateShort(own.activeUntil - DAY)}</div></div>
    </li>`;
  }
  const isNow = slot === art.weekSlot;
  const weeks = Math.round((nextSlotWeekStart(art, slot) - art.weekStart) / (7 * DAY));
  const hint = isNow ? 'Выбери в магазине выше' : `Появится в магазине через ${weeks} ${plural(weeks, ['неделю', 'недели', 'недель'])}`;
  return `<li class="owned owned--empty">
    <span class="owned__icon">${icon(s.icon)}</span>
    <div class="grow"><div class="owned__name">${s.label}</div><div class="owned__meta">${hint}</div></div>
  </li>`;
}

function renewBlock(c, renew) {
  const own = renew.artifact;
  const can = c.points >= renew.price;
  return `<section class="shop-renew">
    <div class="owned__name">Оставить «${esc(own.name)}»?</div>
    <p class="owned__meta">Цикл закончился. Продли ещё на ${cycleWeeks(c.cohort)} ${plural(cycleWeeks(c.cohort), ['неделю', 'недели', 'недель'])}: кэшбэк ${own.cashback}% → ${renew.cashback}% · ${esc(categoryLabel(own.category))}</p>
    <button class="btn btn--primary btn--sm btn--block" data-sheet-action="renew" ${can ? '' : 'disabled'}>${icon('refresh')} Продлить за ${renew.price} баллов</button>
  </section>`;
}

