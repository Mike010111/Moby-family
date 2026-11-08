/**
 * Moby Family — единый глобальный Store.
 *
 * Паттерн: однонаправленный поток данных (Flux-lite).
 *   View ──dispatch(type, payload)──▶ reducer(draft) ──▶ new State ──▶ subscribers ──▶ re-render
 *
 * - Состояние — один сериализуемый объект (без классов и функций).
 * - Редьюсеры мутируют только черновик (structuredClone), исходный state не трогается.
 * - Редьюсер может вернуть результат { ok, reason, ... } — его получает вызывающая сторона
 *   (например, экран оплаты, чтобы показать «Отклонено: превышен лимит»).
 * - Каждый запуск — своя независимая демо-сессия: состояние живёт в sessionStorage
 *   текущей вкладки. Перезагрузка страницы его сохраняет, а новое открытие ссылки
 *   (новая вкладка, другое устройство, повторный запуск) всегда начинается с исходных данных.
 *   Между вкладками и устройствами ничего не передаётся.
 */

const STORAGE_KEY = 'moby.family.demo.v1';
/** Ключ прежних версий прототипа (общая история в localStorage) — удаляем при старте. */
const LEGACY_KEY = 'moby.family.demo.v1';
const LOG_LIMIT = 40;

export class Store {
  #state;
  #reducers;
  #factory;
  #listeners = new Set();

  /**
   * @param {Record<string, Function>} reducers  карта "domain/action" → (draft, payload, ctx) => result
   * @param {() => object} factory             фабрика начального состояния
   */
  constructor(reducers, factory) {
    this.#reducers = reducers;
    this.#factory = factory;
    this.#dropLegacy();
    this.#state = this.#load() ?? factory();
  }

  getState() {
    return this.#state;
  }

  /** Подписка на изменения. Возвращает функцию отписки. */
  subscribe(listener) {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  }

  /**
   * Единственная точка изменения состояния.
   * @returns {{ok: boolean, reason?: string, [k: string]: any}}
   */
  dispatch(type, payload = {}) {
    const reducer = this.#reducers[type];
    if (!reducer) throw new Error(`[store] Unknown action: ${type}`);

    const draft = structuredClone(this.#state);
    const ctx = { now: Date.now(), uid };
    const result = reducer(draft, payload, ctx) ?? { ok: true };

    if (result.noop) return result;

    if (!type.startsWith('ui/')) {
      draft.log.unshift({
        id: uid(),
        type,
        ok: result.ok !== false,
        note: result.logNote ?? result.reason ?? '',
        ts: ctx.now,
      });
      draft.log.length = Math.min(draft.log.length, LOG_LIMIT);
    }

    this.#state = draft;
    this.#persist();
    this.#emit({ type, payload, result });
    return result;
  }

  /** Полный сброс демо-сессии к исходным данным. */
  reset() {
    this.#state = this.#factory();
    this.#persist();
    this.#emit({ type: 'demo/reset', payload: {}, result: { ok: true } });
  }

  #emit(action) {
    for (const listener of this.#listeners) listener(this.#state, action);
  }

  #persist() {
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(this.#state));
    } catch {
      /* приватный режим / квота — демо продолжает работать в памяти */
    }
  }

  #load() {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const parsed = JSON.parse(raw);
      return parsed?.version === this.#factory().version ? parsed : null;
    } catch {
      return null;
    }
  }

  #dropLegacy() {
    try {
      localStorage.removeItem(LEGACY_KEY);
    } catch {
      /* хранилище недоступно — нечего удалять */
    }
  }
}

let counter = 0;
export function uid() {
  counter += 1;
  return `${Date.now().toString(36)}${counter.toString(36)}${Math.random().toString(36).slice(2, 6)}`;
}
