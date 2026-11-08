import { test, expect } from '@playwright/test';

/** Сумма как её печатает format.js: запятая, пробел (в том числе неразрывный) перед BYN. */
const byn = (amount) => new RegExp(`${amount}\\sBYN`);

test.describe('Офлайн-демо (GitHub Pages)', () => {
  let outbound;

  test.beforeEach(async ({ page }) => {
    outbound = [];
    // Шрифты Google и любые адреса вне статики — мимо: страница обязана работать без сети и без бэкенда
    await page.route(/^(?!http:\/\/127\.0\.0\.1:4173\/).*/, (route) => {
      outbound.push(route.request().url());
      return route.abort();
    });
    page.on('request', (req) => {
      if (/\/(api|realms)\//.test(req.url())) outbound.push(req.url());
    });
    page.on('pageerror', (e) => {
      throw e;
    });
  });

  test.afterEach(() => {
    expect(outbound.filter((u) => /\/(api|realms)\//.test(u))).toEqual([]);
  });

  test('все три роли работают в браузере, видна плашка офлайн-режима', async ({ page }) => {
    const screen = page.locator('#screen-scroll');

    await page.goto('#parent');
    await expect(page.locator('.offline-note')).toHaveText(
      'Офлайн-демо: логика в браузере. Настоящий бэкенд — по кнопке Codespaces в README',
    );
    await expect(screen).toContainText('Мой счёт · Анна');
    await expect(screen).toContainText(byn('842,60'));

    await page.getByRole('tab', { name: /Ребёнок 7–13/ }).click();
    await expect(screen).toContainText('Moby-Young (7–13)');
    await expect(screen).toContainText(byn('12,40'));

    await page.getByRole('tab', { name: /Ребёнок 14–17/ }).click();
    await expect(screen).toContainText('Moby-Young (14–17)');
    await expect(screen).toContainText(byn('64,20'));
    await expect(page).toHaveURL(/\/frontend\/#teen$/);
  });

  test('«5 BYN на обед»: запрос ребёнка → одобрение родителем → балансы; «Сбросить демо» возвращает данные', async ({ page }) => {
    const screen = page.locator('#screen-scroll');
    const toasts = page.locator('[data-toast-root]');

    await page.goto('#kid');
    await screen.locator('[data-action="kid/askMoney"]').click();
    await page.getByRole('button', { name: 'Отправить запрос' }).click();

    await page.getByRole('tab', { name: /Главный родственник/ }).click();
    await expect(page.locator('.role-tab__badge')).toHaveText('1');
    await screen.locator('[data-action="parent/approve"]').first().click();
    await expect(toasts).toContainText('Одобрено');
    await expect(screen).toContainText(byn('837,60'));

    await page.getByRole('tab', { name: /Ребёнок 7–13/ }).click();
    await expect(screen).toContainText(byn('17,40'));
    await expect(toasts).toContainText('Мама одобрила запрос: +5,00 BYN');

    await page.getByRole('button', { name: 'Сбросить демо' }).click();
    await expect(toasts).toContainText('Демо-сессия сброшена');
    await expect(screen).toContainText(byn('12,40'));
  });

  test('состояние переживает перезагрузку вкладки (sessionStorage), как в прототипе', async ({ page }) => {
    const screen = page.locator('#screen-scroll');
    await page.goto('#parent');
    await screen.locator('[data-action="parent/toggleFreeze"]').first().click();
    await expect(screen.locator('[data-action="parent/toggleFreeze"]').first()).toContainText('Разблокировать');
    await page.reload();
    await expect(screen.locator('[data-action="parent/toggleFreeze"]').first()).toContainText('Разблокировать');
  });
});
