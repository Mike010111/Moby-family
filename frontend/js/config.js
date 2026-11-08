/**
 * Конфигурация прототипа. Меняется без правки компонентов.
 */
const KEYCLOAK = `${location.protocol}//${location.hostname}:8180`;

export const CONFIG = Object.freeze({
  /** Длительность симуляции сканирования QR, мс. */
  SCAN_DURATION_MS: 1100,

  /** API идёт через тот же origin: nginx проксирует /api на gateway. */
  API_BASE: '',
  /** Keycloak realm и публичный клиент стенда. */
  TOKEN_URL: `${KEYCLOAK}/realms/moby/protocol/openid-connect/token`,
  CLIENT_ID: 'moby-frontend',
  /** Вкладка жюри = вход под демо-пользователем (ADR-005). */
  DEMO_USERS: { parent: 'anna', kid: 'misha', teen: 'ivan' },
  DEMO_PASSWORD: 'demo',
  /** Сервисы, у которых есть эндпоинт сброса демо-данных (профиль demo). */
  RESET_SERVICES: ['payment', 'family', 'rewards', 'notifications'],
});
