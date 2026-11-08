/**
 * Поведение кастомного выпадающего списка (разметка — components.js → dropdown()).
 * Работает делегированием на document: подходит и для экрана, и для шитов.
 * При выборе на корне [data-dd] всплывает событие 'dd:change' с detail.value.
 */

function close(root, { focus = false } = {}) {
  root.classList.remove('is-open');
  root.querySelector('.dd__btn').setAttribute('aria-expanded', 'false');
  root.querySelector('.dd__menu').hidden = true;
  if (focus) root.querySelector('.dd__btn').focus();
}

function open(root) {
  document.querySelectorAll('[data-dd].is-open').forEach((r) => r !== root && close(r));
  root.classList.add('is-open');
  root.querySelector('.dd__btn').setAttribute('aria-expanded', 'true');
  const menu = root.querySelector('.dd__menu');
  menu.hidden = false;
  (menu.querySelector('[aria-selected=true]') ?? menu.querySelector('.dd__option'))?.focus();
}

function select(root, option) {
  const value = option.dataset.value;
  root.dataset.value = value;
  root.querySelector('.dd__label').textContent = option.dataset.display;
  root.querySelectorAll('.dd__option').forEach((o) => o.setAttribute('aria-selected', String(o === option)));
  close(root, { focus: true });
  root.dispatchEvent(new CustomEvent('dd:change', { bubbles: true, detail: { value } }));
}

export function initDropdowns() {
  document.addEventListener('click', (e) => {
    const root = e.target.closest('[data-dd]');
    document.querySelectorAll('[data-dd].is-open').forEach((r) => r !== root && close(r));
    if (!root) return;
    const option = e.target.closest('.dd__option');
    if (option) return select(root, option);
    if (e.target.closest('.dd__btn')) {
      root.classList.contains('is-open') ? close(root) : open(root);
    }
  });

  document.addEventListener(
    'keydown',
    (e) => {
      const root = e.target.closest?.('[data-dd]');
      if (!root) return;
      const options = [...root.querySelectorAll('.dd__option')];
      const i = options.indexOf(document.activeElement);
      if (e.key === 'Escape' && root.classList.contains('is-open')) {
        e.stopPropagation(); // не закрываем шит, только список
        return close(root, { focus: true });
      }
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault();
        if (!root.classList.contains('is-open')) return open(root);
        const next = options[(i + (e.key === 'ArrowDown' ? 1 : -1) + options.length) % options.length];
        next?.focus();
      }
    },
    true,
  );
}
