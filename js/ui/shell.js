/**
 * Оболочка демо-стенда: панель переключения ролей для жюри.
 */
import { bankIcon, icon } from './icons.js';

const ROLE_ICONS = { parent: 'users', kid: 'child', teen: 'teen' };
const ROLES = Object.keys(ROLE_ICONS);

let tabs = [];

export function initShell({ store, onReset }) {
  document.querySelector('[data-brand-mark]').innerHTML = bankIcon(34);

  tabs = [...document.querySelectorAll('[data-role-tab]')];
  for (const tab of tabs) {
    tab.insertAdjacentHTML('afterbegin', icon(ROLE_ICONS[tab.dataset.roleTab]));
    tab.addEventListener('click', () => store.dispatch('ui/setRole', { role: tab.dataset.roleTab }));
  }

  // Клавиатурная навигация по tablist (WAI-ARIA Tabs)
  document.querySelector('[role=tablist]').addEventListener('keydown', (e) => {
    const i = tabs.indexOf(document.activeElement);
    if (i < 0) return;
    const next = { ArrowRight: i + 1, ArrowLeft: i - 1, Home: 0, End: tabs.length - 1 }[e.key];
    if (next === undefined) return;
    e.preventDefault();
    const tab = tabs[(next + tabs.length) % tabs.length];
    tab.focus();
    tab.click();
  });

  const reset = document.querySelector('[data-reset]');
  reset.insertAdjacentHTML('afterbegin', icon('reset'));
  reset.addEventListener('click', onReset);
}

export function renderShell(state) {
  const pending = state.requests.filter((r) => r.status === 'pending').length;

  for (const tab of tabs) {
    const role = tab.dataset.roleTab;
    const selected = role === state.ui.role;
    tab.setAttribute('aria-selected', String(selected));
    tab.tabIndex = selected ? 0 : -1;
    tab.querySelector('.role-tab__badge')?.remove();
    if (role === 'parent' && pending) {
      tab.insertAdjacentHTML('beforeend', `<span class="role-tab__badge" aria-label="${pending} ожидают решения">${pending}</span>`);
    }
  }
}

export function roleFromHash() {
  const role = location.hash.replace('#', '');
  return ROLES.includes(role) ? role : null;
}
