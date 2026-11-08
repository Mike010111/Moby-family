/**
 * Расчёты, которые экран показывает до операции: округление, «Доступно» в сканере QR, котировка P2P.
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Store } from './store.js';
import { reducers } from './actions.js';
import { createInitialState } from './initial-state.js';
import { quoteP2P, round2, spendable } from '../js/core/rules.js';
import { cashbackAmount } from '../js/core/artifacts.js';

const fresh = () => new Store(reducers, createInitialState);

test('Округление до копеек HALF_UP, как на бэкенде', () => {
  assert.equal(round2(0.015), 0.02);
  assert.equal(round2((0.3 * 5) / 100), 0.02);
  assert.equal(round2(1.005), 1.01);
  assert.equal(round2(0.1 + 0.2), 0.3);
  assert.equal(round2(-2.345), -2.35);
  assert.equal(cashbackAmount(0.3, 5), 0.02);
  assert.equal(cashbackAmount(2.4, 5), 0.12);
  assert.equal(cashbackAmount(0.1, 3), 0.01);
});

test('«Доступно» в сканере QR — баланс, урезанный общими лимитами, и обновляется после каждой оплаты', () => {
  const store = fresh();
  const misha = () => store.getState().children.misha;
  // 12,40 на карте, но лимит «Сегодня · Все категории» 15,00 и уже потрачено 3,60 → 11,40
  assert.equal(spendable(misha()), 11.4);
  for (let i = 0; i < 4; i += 1) assert.equal(store.dispatch('pay/qr', { childId: 'misha', merchantId: 'm-buffet' }).ok, true);
  assert.equal(misha().balance, 2.8);
  assert.equal(spendable(misha()), 1.8);
  const res = store.dispatch('pay/qr', { childId: 'misha', merchantId: 'm-buffet' });
  assert.equal(res.code, 'LIMIT');
  assert.match(res.reason, /осталось 1,80 BYN/);

  store.dispatch('parent/toggleFreeze', { childId: 'misha' });
  assert.equal(spendable(misha()), 0);
});

test('P2P: сумма больше баланса — «Недостаточно средств», а не запрос одобрения сверх лимита', () => {
  const ivan = fresh().getState().children.ivan; // 64,20 на карте, лимит на переводы — осталось 24,50
  assert.equal(quoteP2P(ivan, 35).mode, 'request');
  const q = quoteP2P(ivan, 100);
  assert.equal(q.mode, 'blocked');
  assert.equal(q.code, 'FUNDS');
});
