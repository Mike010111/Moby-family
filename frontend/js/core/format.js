/** Форматирование и безопасный вывод. */

const moneyFmt = new Intl.NumberFormat('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const intFmt = new Intl.NumberFormat('ru-RU');

export const money = (n) => `${moneyFmt.format(n)} BYN`;
export const moneyBare = (n) => moneyFmt.format(n);
export const int = (n) => intFmt.format(n);

/** Знаковая сумма для истории операций: «+10,00» / «−2,40». */
export const signed = (n) => `${n >= 0 ? '+' : '−'}${moneyFmt.format(Math.abs(n))}`;

export function ago(ts, now = Date.now()) {
  const s = Math.max(0, Math.round((now - ts) / 1000));
  if (s < 45) return 'только что';
  const m = Math.round(s / 60);
  if (m < 60) return `${m} мин назад`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h} ч назад`;
  const d = Math.round(h / 24);
  return d === 1 ? 'вчера' : `${d} дн. назад`;
}

export function clock(ts = Date.now()) {
  return new Date(ts).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
}

export function daysUntil(iso, years = 18, now = Date.now()) {
  const target = new Date(iso);
  target.setFullYear(target.getFullYear() + years);
  return Math.ceil((target.getTime() - now) / 86_400_000);
}

export function dateLong(iso, addYears = 0) {
  const d = new Date(iso);
  d.setFullYear(d.getFullYear() + addYears);
  return d.toLocaleDateString('ru-RU', { day: 'numeric', month: 'long', year: 'numeric' });
}

/** Экранирование пользовательского ввода перед вставкой в шаблон. */
export function esc(value) {
  return String(value ?? '').replace(/[&<>"']/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[ch]);
}

/** plural(3, ['запрос', 'запроса', 'запросов']) → 'запроса' */
export function plural(n, [one, few, many]) {
  const m10 = n % 10;
  const m100 = n % 100;
  if (m10 === 1 && m100 !== 11) return one;
  if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return few;
  return many;
}

export const pct =(part, whole) => (whole > 0 ? Math.max(0, Math.min(100, Math.round((part / whole) * 100))) : 0);
