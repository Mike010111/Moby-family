/**
 * Офлайн-демо: прототипный Store с редьюсерами и начальным состоянием из spec/ — вся логика в браузере,
 * как до появления бэкенда. Интерфейс тот же, что у ApiStore (getState, subscribe, dispatch, reset),
 * поэтому экраны не знают, в каком режиме работают. Состояние — в sessionStorage вкладки,
 * «Сбросить демо» возвращает исходные данные.
 */
import { Store } from '../../spec/store.js';
import { reducers } from '../../spec/actions.js';
import { createInitialState } from '../../spec/initial-state.js';

export function createOfflineStore(role) {
  const store = new Store(reducers, createInitialState);
  store.dispatch('ui/setRole', { role });
  return store;
}
