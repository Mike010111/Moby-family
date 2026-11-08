/**
 * Конфигурация прототипа. Меняется без правки компонентов.
 * Адреса бэкенда приходят из config.js в корне фронтенда (window.MOBY_CONFIG).
 */
const RUNTIME = globalThis.MOBY_CONFIG ?? {};
const API_BASE = (RUNTIME.apiBaseUrl ?? '').replace(/\/+$/, '');
const KEYCLOAK = (RUNTIME.keycloakUrl ?? '').replace(/\/+$/, '') || `${location.protocol}//${location.hostname}:8180`;

export const CONFIG = Object.freeze({
  /** Длительность симуляции сканирования QR, мс. */
  SCAN_DURATION_MS: 1100,

  /** Адрес бэкенда задан; иначе — офлайн-демо (логика из spec/ в браузере). */
  BACKEND: Boolean(RUNTIME.apiBaseUrl),
  /** База для /api ('.' — тот же origin). */
  API_BASE,
  /** Бэкенд считается недоступным, если health через gateway не ответил за это время, мс. */
  HEALTH_TIMEOUT_MS: 3000,
  /** Keycloak realm и публичный клиент стенда. */
  TOKEN_URL: `${KEYCLOAK}/realms/moby/protocol/openid-connect/token`,
  CLIENT_ID: 'moby-frontend',
  /** Вкладка жюри = вход под демо-пользователем (ADR-005). */
  DEMO_USERS: { parent: 'anna', kid: 'misha', teen: 'ivan' },
  DEMO_PASSWORD: 'demo',
  /** Сервисы, у которых есть эндпоинт сброса демо-данных (профиль demo). */
  RESET_SERVICES: ['payment', 'family', 'rewards', 'notifications'],
});
