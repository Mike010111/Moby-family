/**
 * Moby Family — слой данных поверх бэкенда (через gateway).
 *
 * Интерфейс тот же, что у прежнего Store, поэтому экраны не меняются:
 *   getState()            — снимок в форме прототипа (parent · relatives · children · requests · feed · log · ui);
 *   subscribe(listener)   — подписка на изменения;
 *   dispatch(type, data)  — операция «<домен>/<действие>» → вызов API → Promise<{ ok, code?, reason?, … }>;
 *   reset()               — сброс демо-данных во всех сервисах (профиль demo).
 *
 * Состояние собирается из ответов payment / family / rewards / notification и после каждой операции
 * перечитывается. Решения принимает бэкенд; чистые правила (rules.js, artifacts.js) экран использует только
 * для подписей и подсказок. Вкладка жюри — вход под демо-пользователем Keycloak (ADR-005);
 * уведомления приходят по SSE, и экран перечитывает данные.
 */
import { CONFIG } from '../config.js';

const LOG_LIMIT = 40;

/** Ошибка API в терминах прототипа: code из ProblemDetail, reason — его detail. */
class ApiError extends Error {
  constructor(status, problem) {
    super(problem?.detail ?? `HTTP ${status}`);
    this.status = status;
    this.code = problem?.code;
    this.reason = problem?.detail ?? 'Сервис временно недоступен';
    const { type, title, status: _s, detail, instance, code, ...extra } = problem ?? {};
    this.extra = extra;
  }
}

const ms = (iso) => (iso ? Date.parse(iso) : undefined);
const lower = (s) => (s ? String(s).toLowerCase() : s);
const kebab = (s) => lower(s)?.replace(/_/g, '-');
const uid = () => (crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`);

/* ───────────── Преобразование ответов API в форму состояния прототипа ───────────── */

function mapOwned(o) {
  if (!o) return null;
  return { id: o.id, slot: o.slot, name: o.name, rarity: o.rarity, category: o.category, cashback: o.cashback, cycles: o.cycles, boughtAt: ms(o.boughtAt), activeUntil: ms(o.activeUntil) };
}

function mapChild(member, account, operations, rewards, inbox) {
  return {
    id: member.id,
    name: member.name,
    nameGen: member.nameGen,
    surname: member.surname,
    age: member.age,
    cohort: lower(member.cohort),
    balance: account.balance,
    points: rewards.points,
    card: { ...account.card, frozen: account.frozen },
    artifacts: {
      cohort: lower(rewards.cohort),
      weekSlot: rewards.weekSlot,
      weekStart: ms(rewards.weekStart),
      owned: Object.fromEntries(Object.entries(rewards.owned).map(([slot, o]) => [slot, mapOwned(o)])),
      soldOut: rewards.soldOut,
    },
    allow: account.allow,
    limits: account.limits.map((l) => ({ id: l.id, count: l.count, unit: l.unit, amount: l.amount, categories: l.categories, start: ms(l.start) })),
    autopay: account.autopay ?? { enabled: false, amount: 0, every: 'week', weekday: 0, monthDay: 1 },
    piggies: member.piggies.map((p) => ({ id: p.id, goal: p.goal, target: p.target, saved: p.saved })),
    tasks: member.tasks.map((t) => ({ id: t.id, title: t.title, reward: t.reward, status: lower(t.status) })),
    lessons: { done: member.lessons.done },
    geo: { ...member.geo, updated: ms(member.geo.updated) },
    battery: member.battery,
    approvals: account.approvals.map((a) => ({ id: a.id, requestId: a.requestId, max: a.max, status: lower(a.status), usedAmount: a.usedAmount, ts: ms(a.createdAt) })),
    inbox: inbox.map((n) => ({ id: n.id, text: n.text, tone: n.tone, ts: ms(n.ts), seen: n.seen })),
    tx: operations.map((o) => ({
      id: o.id,
      title: o.title,
      amount: o.amount,
      mcc: o.mcc,
      ts: ms(o.ts),
      status: o.status === 'COMPLETED' ? 'ok' : 'declined',
      note: o.note ?? undefined,
      online: o.online,
    })),
    subscriptions: account.subscriptions,
    stats: account.stats ?? { txCount: 0, since: '' },
  };
}

const mapRequest = (r) => ({
  id: r.id,
  kind: kebab(r.kind),
  childId: r.childId,
  relativeId: r.relativeId,
  amount: r.amount,
  reason: r.reason,
  contactName: r.contactName ?? undefined,
  piggyId: r.piggyId ?? undefined,
  status: lower(r.status),
  resolution: r.resolution ?? undefined,
  ts: ms(r.ts),
  resolvedAt: ms(r.resolvedAt),
});

const mapFeed = (f) => ({ id: f.id, kind: f.kind, childId: f.childId, title: f.title, text: f.text, ts: ms(f.ts), requestId: f.requestId ?? undefined, taskId: f.taskId ?? undefined });

/* ───────────── Операции: тип прототипа → вызов API → результат для экрана ───────────── */

const relativeFrom = (state, created) => state.relatives.find((r) => r.id === created.relativeId) ?? { dative: created.relativeDative, label: created.relativeLabel };
const child = (state, id) => state.children[id];

const ACTIONS = {
  'request/create': async (api, { childId, relativeId, amount, reason }, state) => {
    const created = await api.post('/api/family/requests', { childId, relativeId, amount, reason });
    return { id: created.request.id, relative: relativeFrom(state, created) };
  },
  'request/approve': (api, { id }) => api.post(`/api/family/requests/${id}/approve`),
  'request/decline': (api, { id }) => api.post(`/api/family/requests/${id}/decline`),

  'limit/create': (api, { childId, ...draft }) => api.post(`/api/payment/children/${childId}/limits`, draft),
  'limit/update': (api, { childId, id, ...draft }) => api.put(`/api/payment/children/${childId}/limits/${id}`, draft),
  'limit/delete': (api, { childId, id }) => api.del(`/api/payment/children/${childId}/limits/${id}`),

  'parent/toggleAllow': (api, { childId, key }, state) =>
    api.put(`/api/payment/children/${childId}/categories/${key}`, { allowed: !child(state, childId).allow[key] }),
  'parent/toggleFreeze': (api, { childId }, state) =>
    api.put(`/api/payment/children/${childId}/frozen`, { frozen: !child(state, childId).card.frozen }),
  'parent/topUp': (api, { childId, amount }) => api.post(`/api/payment/children/${childId}/topups`, { amount }, { idempotent: true }),
  'autopay/update': (api, { childId, enabled, amount, every, weekday, monthDay }) =>
    api.put(`/api/payment/children/${childId}/autopay`, { enabled, amount, every, weekday: Number(weekday), monthDay: Number(monthDay) }),
  'parent/refreshGeo': (api, { childId }) => api.post(`/api/family/children/${childId}/geo/refresh`),
  'parent/addTask': (api, { childId, title, reward }) => api.post(`/api/family/children/${childId}/tasks`, { title, reward: String(reward ?? '') }),

  'task/complete': (api, { childId, taskId }) => api.post(`/api/family/children/${childId}/tasks/${taskId}/submit`),
  'task/approve': (api, { childId, taskId }) => api.post(`/api/family/children/${childId}/tasks/${taskId}/approve`),
  'lesson/complete': (api, { childId }) => api.post(`/api/family/children/${childId}/lessons/complete`),

  'pay/qr': async (api, { childId, merchantId }) => {
    const res = await api.post(`/api/payment/children/${childId}/qr-payments`, { merchantId }, { idempotent: true });
    return {
      merchant: res.merchant,
      earned: res.earned ?? Math.max(1, Math.round(res.merchant.amount)),
      legal: res.legal,
      cashback: res.cashback ?? null,
    };
  },

  'piggy/create': async (api, { childId, goal, target }) => ({ id: (await api.post(`/api/family/children/${childId}/piggies`, { goal, target })).id }),
  'piggy/deposit': async (api, { childId, piggyId, amount }) => {
    const res = await api.post(`/api/family/children/${childId}/piggies/${piggyId}/deposits`, { amount }, { idempotent: true });
    return { completed: res.completed ?? null };
  },
  'piggy/update': async (api, { childId, piggyId, target }) => piggyResult(await api.put(`/api/family/children/${childId}/piggies/${piggyId}/target`, { target })),
  'piggy/delete': async (api, { childId, piggyId }) => piggyResult(await api.del(`/api/family/children/${childId}/piggies/${piggyId}`)),

  'p2p/send': async (api, { childId, contactId, amount }) =>
    ({ mode: lower((await api.post(`/api/payment/children/${childId}/p2p-transfers`, { contactId, amount }, { idempotent: true })).mode) }),
  'p2p/requestOver': async (api, { childId, contactId, amount }, state) =>
    ({ relative: relativeFrom(state, await api.post('/api/family/requests/p2p-over', { childId, contactId, amount })) }),

  'artifact/buy': async (api, { childId, id }) => {
    const res = await api.post(`/api/rewards/children/${childId}/artifacts/${id}/purchase`);
    return { artifact: mapOwned(res.artifact), replaced: res.replaced ?? null };
  },
  'artifact/renew': (api, { childId }) => api.post(`/api/rewards/children/${childId}/artifacts/renew`),

  'sub/toggle': (api, { childId, id }, state) =>
    api.patch(`/api/payment/children/${childId}/subscriptions/${id}`, { active: !child(state, childId).subscriptions.find((s) => s.id === id)?.active }),
  'sub/update': (api, { childId, id, price }) => api.patch(`/api/payment/children/${childId}/subscriptions/${id}`, { price }),
  'sub/delete': (api, { childId, id }) => api.del(`/api/payment/children/${childId}/subscriptions/${id}`),
};

function piggyResult(res) {
  return {
    pending: res.pending,
    relative: res.pending ? { dative: res.relativeDative, label: res.relativeLabel } : undefined,
    completed: res.completed ?? null,
    returned: res.returned ?? 0,
  };
}

/* ───────────── Store ───────────── */

export class ApiStore {
  #state;
  /** Под кем выполняются запросы; ui.role (что рисуется) меняется только после загрузки данных этой роли. */
  #role;
  #listeners = new Set();
  #tokens = new Map(); // role → { token, exp }
  #stream = null;
  #reloadTimer = null;

  /** Входит под демо-пользователем вкладки и загружает его данные. */
  static async connect(role = 'parent') {
    const store = new ApiStore();
    store.#role = role;
    store.#state = { ui: { role, selectedChildId: 'misha' }, parent: null, relatives: [], children: {}, requests: [], feed: [], log: [] };
    await store.#reload();
    store.#openStream();
    return store;
  }

  getState() {
    return this.#state;
  }

  subscribe(listener) {
    this.#listeners.add(listener);
    return () => this.#listeners.delete(listener);
  }

  async dispatch(type, payload = {}) {
    if (type.startsWith('ui/')) return this.#ui(type, payload);
    const action = ACTIONS[type];
    if (!action) throw new Error(`[store] Unknown action: ${type}`);

    let result;
    try {
      result = { ok: true, ...((await action(this.#api, payload, this.#state)) ?? {}) };
    } catch (e) {
      result = e instanceof ApiError ? { ok: false, code: e.code, reason: e.reason, ...e.extra } : { ok: false, reason: 'Сервис временно недоступен' };
      if (!(e instanceof ApiError)) console.error(e);
    }
    await this.#reload().catch((e) => console.error('[store] reload failed', e));
    this.#log(type, result);
    this.#emit({ type, payload, result });
    return result;
  }

  /** «Сбросить демо»: seed-данные во всех сервисах заново. */
  async reset() {
    for (const svc of CONFIG.RESET_SERVICES) await this.#api.post(`/api/${svc}/demo/reset`);
    this.#state.log = [];
    await this.#reload();
    this.#emit({ type: 'demo/reset', payload: {}, result: { ok: true } });
  }

  /* ── UI-действия: локально, без бэкенда (кроме отметки «прочитано») ── */

  async #ui(type, payload) {
    const ui = this.#state.ui;
    if (type === 'ui/setRole') {
      if (this.#role === payload.role) return { ok: true, noop: true };
      this.#role = payload.role;
      await this.#reload();
      if (this.#role !== payload.role) return { ok: true, noop: true }; // успели переключить ещё раз
      ui.role = payload.role;
      this.#openStream();
    } else if (type === 'ui/selectChild') {
      if (ui.selectedChildId === payload.childId || !this.#state.children[payload.childId]) return { ok: true, noop: true };
      ui.selectedChildId = payload.childId;
    } else if (type === 'ui/markSeen') {
      const c = this.#state.children[payload.childId];
      if (!c?.inbox.some((n) => !n.seen)) return { ok: true, noop: true };
      c.inbox.forEach((n) => (n.seen = true));
      this.#api.post('/api/notifications/inbox/seen', { childId: payload.childId }).catch(() => {});
    }
    this.#emit({ type, payload, result: { ok: true } });
    return { ok: true };
  }

  /* ── Загрузка состояния под текущей ролью ── */

  async #reload() {
    const api = this.#api;
    const role = this.#role;
    const me = await api.get('/api/family/me');
    const parentRole = me.me.role === 'PARENT';
    const [requests, children, parentAccount, parentPoints, feed] = await Promise.all([
      api.get('/api/family/requests'),
      Promise.all(
        me.children.map(async (m) => {
          const [account, operations, rewards, inbox] = await Promise.all([
            api.get(`/api/payment/children/${m.id}/account`),
            api.get(`/api/payment/children/${m.id}/operations?limit=60`),
            api.get(`/api/rewards/children/${m.id}`),
            api.get(`/api/notifications/inbox?childId=${m.id}`),
          ]);
          return mapChild(m, account, operations, rewards, inbox);
        }),
      ),
      parentRole ? api.get('/api/payment/me/parent-account') : null,
      parentRole ? api.get('/api/rewards/me/points') : null,
      parentRole ? api.get('/api/notifications/feed') : [],
    ]);
    if (this.#role !== role) return; // пока грузили, переключили вкладку — данные устарели

    const state = this.#state;
    state.relatives = me.relatives;
    state.children = Object.fromEntries(children.map((c) => [c.id, c]));
    state.requests = requests.map(mapRequest);
    state.feed = feed.map(mapFeed);
    state.parent = parentAccount && {
      id: parentAccount.memberId,
      name: parentAccount.name,
      balance: parentAccount.balance,
      points: parentPoints.points,
      cardMask: parentAccount.cardMask,
      packageName: parentAccount.packageName,
    };
    if (!state.children[state.ui.selectedChildId]) state.ui.selectedChildId = children[0]?.id;
  }

  /* ── SSE: новые уведомления и изменения данных → перечитать состояние ── */

  async #openStream() {
    this.#stream?.abort();
    const ctrl = new AbortController();
    this.#stream = ctrl;
    try {
      const res = await fetch(`${CONFIG.API_BASE}/api/notifications/stream`, {
        headers: { Authorization: `Bearer ${await this.#token()}`, Accept: 'text/event-stream' },
        signal: ctrl.signal,
      });
      const reader = res.body.pipeThrough(new TextDecoderStream()).getReader();
      let buffer = '';
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += value.replace(/\r\n/g, '\n');
        let end;
        while ((end = buffer.indexOf('\n\n')) >= 0) {
          const event = /^event:\s*(.*)$/m.exec(buffer.slice(0, end))?.[1];
          buffer = buffer.slice(end + 2);
          if (event === 'notification' || event === 'changed') this.#scheduleReload();
        }
      }
    } catch {
      if (ctrl.signal.aborted) return;
    }
    if (!ctrl.signal.aborted) setTimeout(() => this.#stream === ctrl && this.#openStream(), 3000);
  }

  #scheduleReload() {
    clearTimeout(this.#reloadTimer);
    this.#reloadTimer = setTimeout(async () => {
      await this.#reload().catch(() => {});
      this.#emit({ type: 'sse/update', payload: {}, result: { ok: true } });
    }, 150);
  }

  /* ── HTTP ── */

  #api = {
    get: (path) => this.#request('GET', path),
    post: (path, body, opts) => this.#request('POST', path, body, opts),
    put: (path, body) => this.#request('PUT', path, body),
    patch: (path, body) => this.#request('PATCH', path, body),
    del: (path) => this.#request('DELETE', path),
  };

  /**
   * Денежные операции идут с Idempotency-Key: один ключ на одно действие пользователя,
   * повтор после истёкшего токена — с тем же ключом.
   */
  async #request(method, path, body, { idempotent = false, key = idempotent ? uid() : null, retried = false } = {}) {
    const headers = { Authorization: `Bearer ${await this.#token()}`, Accept: 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (key) headers['Idempotency-Key'] = key;
    const res = await fetch(`${CONFIG.API_BASE}${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    if (res.status === 401 && !retried) {
      this.#tokens.delete(this.#role);
      return this.#request(method, path, body, { idempotent, key, retried: true });
    }
    const text = await res.text();
    const json = text ? JSON.parse(text) : null;
    if (!res.ok) throw new ApiError(res.status, json);
    return json;
  }

  async #token() {
    const role = this.#role;
    const cached = this.#tokens.get(role);
    if (cached && cached.exp > Date.now()) return cached.token;
    const res = await fetch(CONFIG.TOKEN_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ grant_type: 'password', client_id: CONFIG.CLIENT_ID, username: CONFIG.DEMO_USERS[role], password: CONFIG.DEMO_PASSWORD }),
    });
    if (!res.ok) throw new Error(`Keycloak: вход ${CONFIG.DEMO_USERS[role]} не удался (${res.status})`);
    const { access_token: token, expires_in: ttl } = await res.json();
    this.#tokens.set(role, { token, exp: Date.now() + (ttl - 30) * 1000 });
    return token;
  }

  #log(type, result) {
    this.#state.log.unshift({ id: uid(), type, ok: result.ok !== false, note: result.reason ?? '', ts: Date.now() });
    this.#state.log.length = Math.min(this.#state.log.length, LOG_LIMIT);
  }

  #emit(action) {
    for (const listener of this.#listeners) listener(this.#state, action);
  }
}
