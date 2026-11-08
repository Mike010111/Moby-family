import { defineConfig } from '@playwright/test';

/**
 * Офлайн-демо как на GitHub Pages: статика без бэкенда, сайт не в корне домена (…github.io/<repo>/).
 * python3 -m http.server раздаёт корень репозитория, страница открывается из /frontend/.
 */
export default defineConfig({
  testDir: 'e2e',
  timeout: 30_000,
  reporter: [['list']],
  use: {
    baseURL: 'http://127.0.0.1:4173/frontend/',
    viewport: { width: 1280, height: 800 },
  },
  webServer: {
    command: 'python3 -m http.server 4173 --bind 127.0.0.1 --directory ..',
    url: 'http://127.0.0.1:4173/frontend/index.html',
    reuseExistingServer: !process.env.CI,
    stderr: 'ignore',
  },
});
